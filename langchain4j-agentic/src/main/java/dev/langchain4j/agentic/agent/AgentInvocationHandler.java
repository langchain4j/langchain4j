package dev.langchain4j.agentic.agent;

import dev.langchain4j.agentic.internal.InternalAgent;
import dev.langchain4j.agentic.observability.AfterAgentToolExecution;
import dev.langchain4j.agentic.observability.AgentListener;
import dev.langchain4j.agentic.observability.AgentMonitor;
import dev.langchain4j.agentic.observability.BeforeAgentToolExecution;
import dev.langchain4j.agentic.observability.ComposedAgentListener;
import dev.langchain4j.agentic.observability.MonitoredAgent;
import dev.langchain4j.agentic.planner.AgentArgument;
import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.internal.AgenticScopeOwner;
import dev.langchain4j.agentic.planner.AgenticSystemConfigurationException;
import dev.langchain4j.agentic.planner.AgenticSystemTopology;
import dev.langchain4j.agentic.planner.Planner;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.agentic.scope.DefaultAgenticScope;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.invocation.LangChain4jManaged;
import dev.langchain4j.observability.api.event.AiServiceResponseReceivedEvent;
import dev.langchain4j.observability.api.listener.AiServiceListener;
import dev.langchain4j.observability.api.listener.AiServiceResponseReceivedListener;
import dev.langchain4j.service.AiServiceContext;
import dev.langchain4j.service.ParameterNameResolver;
import dev.langchain4j.service.memory.ChatMemoryAccess;
import dev.langchain4j.service.memory.ChatMemoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import static dev.langchain4j.agentic.observability.ComposedAgentListener.composeWithInherited;
import static dev.langchain4j.agentic.observability.ComposedAgentListener.listenerOfType;
import static dev.langchain4j.agentic.observability.ListenerNotifierUtil.afterAgentInvocation;
import static dev.langchain4j.agentic.observability.ListenerNotifierUtil.agentError;
import static dev.langchain4j.agentic.observability.ListenerNotifierUtil.beforeAgentInvocation;
import static dev.langchain4j.agentic.scope.DefaultAgenticScope.ephemeralAgenticScope;

public class AgentInvocationHandler implements InvocationHandler, InternalAgent {

    private static final Logger LOGGER = LoggerFactory.getLogger(AgentInvocationHandler.class);

    private final AiServiceContext context;
    private final AgentBuilder<?, ?> builder;
    private final Object agent;
    private final boolean agenticScopeDependent;
    private final Function<AgenticScope, ChatModel> chatModelProvider;
    private final Function<AgenticScope, StreamingChatModel> streamingChatModelProvider;
    private String agentId;
    private InternalAgent parent;
    private AgentListener agentListener;

    private final Map<Object, AiServiceResponseReceivedEvent> lastResponseEvents = new ConcurrentHashMap<>();

    AgentInvocationHandler(
            AiServiceContext context,
            Object agent,
            AgentBuilder<?, ?> builder,
            boolean agenticScopeDependent) {
        this.context = context;
        this.agent = agent;
        this.builder = builder;
        this.agentId = builder.name;
        this.agenticScopeDependent = agenticScopeDependent;
        this.agentListener = builder.agentListener;
        this.chatModelProvider = builder.chatModelProvider;
        this.streamingChatModelProvider = builder.streamingChatModelProvider;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Exception {
        if (method.getDeclaringClass() == AiServiceListener.class || method.getDeclaringClass() == AiServiceResponseReceivedListener.class) {
            return switch (method.getName()) {
                case "getEventClass" -> AiServiceResponseReceivedEvent.class;
                case "onEvent" -> {
                    AiServiceResponseReceivedEvent event = (AiServiceResponseReceivedEvent) args[0];
                    AgenticScope agenticScope = (AgenticScope) event.invocationContext().managedParameters().get(AgenticScope.class);
                    lastResponseEvents.put(agenticScope.memoryId(), event);
                    yield null;
                }
                default ->
                        throw new UnsupportedOperationException(
                                "Unknown method on AiServiceResponseReceivedListener class : " + method.getName());
            };
        }

        if (method.getDeclaringClass() == ChatMessagesAccess.class) {
            if ("removeLastResponseEvent".equals(method.getName())) {
                lastResponseEvents.remove(args[0]);
                return null;
            }
            AiServiceResponseReceivedEvent lastResponseEvent = lastResponseEvents.get(args[0]);
            if (lastResponseEvent == null) {
                return null;
            }
            return switch (method.getName()) {
                case "lastUserMessage" -> lastUserMessage(lastResponseEvent.request().messages()).orElse(null);
                case "lastChatRequest" -> lastResponseEvent.request();
                case "lastChatResponse" -> lastResponseEvent.response();
                default ->
                    throw new UnsupportedOperationException(
                            "Unknown method on AgenticScopeOwner class : " + method.getName());
            };
        }

        if (method.getDeclaringClass() == AgenticScopeOwner.class) {
            return switch (method.getName()) {
                case "withAgenticScope" -> {
                    if (!agenticScopeDependent) {
                        yield proxy;
                    }
                    Object agentProxy = ((DefaultAgenticScope) args[0]).getOrCreateAgent(agentId, builder::build);
                    AgentInvocationHandler agent = (AgentInvocationHandler) Proxy.getInvocationHandler(agentProxy);
                    agent.setParent(parent);
                    agent.agentId = agentId;
                    yield agentProxy;
                }
                case "registry" ->
                    throw new UnsupportedOperationException(
                            "AgenticScopeOwner's registry method can be used only on the root agent of an agentic system.");
                default ->
                    throw new UnsupportedOperationException(
                            "Unknown method on AgenticScopeOwner class : " + method.getName());
            };
        }

        if (method.getDeclaringClass() == ChatMemoryAccess.class) {
            return switch (method.getName()) {
                case "getChatMemory" ->
                    context.hasChatMemory() && (ChatMemoryService.DEFAULT.equals(args[0]) || builder.hasNonDefaultChatMemory()) ?
                            context.chatMemoryService.getChatMemory(args[0]) :
                            null;
                case "evictChatMemory" ->
                    context.hasChatMemory() && context.chatMemoryService.evictChatMemory(args[0]) != null;
                default ->
                    throw new UnsupportedOperationException(
                            "Unknown method on ChatMemoryAccess class : " + method.getName());
            };
        }

        if (method.getDeclaringClass() == AgentInstance.class || method.getDeclaringClass() == InternalAgent.class) {
            return method.invoke(Proxy.getInvocationHandler(proxy), args);
        }

        if (method.getDeclaringClass() == MonitoredAgent.class) {
            return listenerOfType(agentListener, AgentMonitor.class);
        }

        if (method.getDeclaringClass() == Object.class) {
            return switch (method.getName()) {
                case "toString" -> "Agent<" + builder.agentServiceClass.getSimpleName() + ">";
                case "hashCode" -> System.identityHashCode(agent);
                default ->
                        throw new UnsupportedOperationException(
                                "Unknown method on Object class : " + method.getName());
            };
        }

        AgenticScope agenticScope = LangChain4jManaged.current(AgenticScope.class);
        if (agenticScope != null) {
            if (chatModelProvider != null) {
                context.chatModel = chatModelProvider.apply(agenticScope);
            } else if (streamingChatModelProvider != null) {
                context.streamingChatModel = streamingChatModelProvider.apply(agenticScope);
            }
            return method.invoke(agent, args);
        }
        return invokeStandaloneAgent(method, args);
    }

    private Object invokeStandaloneAgent(Method method, Object[] args) {
        LOGGER.warn("Improper invocation of a standalone agent outside of an agentic system, consider using AiServices instead.");

        AgenticScope standaloneAgenticScope = ephemeralAgenticScope();
        LangChain4jManaged.setCurrent(Map.of(AgenticScope.class, standaloneAgenticScope));

        Map<String, Object> namedArgs = argToMap(method, args);
        beforeAgentInvocation(agentListener, standaloneAgenticScope, this, namedArgs);

        Object result = null;
        try {
            result = method.invoke(agent, args);
        } catch (Exception e) {
            AgentInvocationException invocationException = new AgentInvocationException("Failed to invoke agent method: " + method, e);
            agentError(agentListener, standaloneAgenticScope, this, namedArgs, invocationException);
            throw invocationException;
        } finally {
            LangChain4jManaged.removeCurrent();
            if (result != null) {
                afterAgentInvocation(agentListener, standaloneAgenticScope, this, namedArgs, result);
            }
        }
        return result;
    }

    private static Map<String, Object> argToMap(Method method, Object[] args) {
        if (method.getParameterCount() == 1 && Map.class.isAssignableFrom(method.getParameters()[0].getType())) {
            return (Map<String, Object>) args[0];
        }
        if (args == null || args.length == 0) {
            return Map.of();
        }
        Map<String, Object> namedArgs = new HashMap<>();
        for (int i = 0; i < args.length; i++) {
            namedArgs.put(ParameterNameResolver.name(method.getParameters()[i]), args[i]);
        }
        return namedArgs;
    }

    private static Optional<UserMessage> lastUserMessage(Collection<ChatMessage> messages) {
        // TODO replace with UserMessage.findLast(messages)
        return messages.stream()
                .filter(UserMessage.class::isInstance)
                .map(UserMessage.class::cast)
                .reduce((first, second) -> second);
    }

    @Override
    public String toString() {
        return "Agent<" + builder.agentServiceClass.getSimpleName() + ">";
    }

    @Override
    public void setParent(InternalAgent parent) {
        if (builder.hasChatMemory() && parent != null && !parent.allowChatMemory()) {
            throw new AgenticSystemConfigurationException("Agents with chat memory can't be a subagent of " + parent.type());
        }
        this.parent = parent;
        registerInheritedParentListener(parent.listener());
    }

    @Override
    public void registerInheritedParentListener(AgentListener parentListener) {
        if (parentListener != null && parentListener.inheritedBySubagents() && isNewListener(agentListener, parentListener)) {
            agentListener = composeWithInherited(agentListener, parentListener);
            context.toolService.beforeToolExecution(beforeToolExecution ->
                    agentListener.beforeAgentToolExecution(new BeforeAgentToolExecution(this, beforeToolExecution)));
            context.toolService.afterToolExecution(toolExecution ->
                    agentListener.afterAgentToolExecution(new AfterAgentToolExecution(this, toolExecution)));
        }
    }

    private static boolean isNewListener(AgentListener currentListener, AgentListener newListener) {
        if (newListener == currentListener) {
            return false;
        }
        if (currentListener instanceof ComposedAgentListener composed) {
            return !composed.contains(newListener);
        }
        return true;
    }

    @Override
    public void appendId(String idSuffix) {
        this.agentId = this.agentId + idSuffix;
    }

    @Override
    public AgentListener listener() {
        return agentListener;
    }

    @Override
    public Class<?> type() {
        return builder.agentServiceClass;
    }

    @Override
    public Class<? extends Planner> plannerType() {
        return null;
    }

    @Override
    public String name() {
        return builder.name;
    }

    @Override
    public String agentId() {
        return agentId;
    }

    @Override
    public String description() {
        return builder.description;
    }

    @Override
    public Type outputType() {
        return builder.agentReturnType;
    }

    @Override
    public String outputKey() {
        return builder.outputKey;
    }

    @Override
    public boolean async() {
        return builder.async;
    }

    @Override
    public boolean optional() {
        return builder.optional;
    }

    @Override
    public List<AgentArgument> arguments() {
        return builder.arguments;
    }

    @Override
    public AgentInstance parent() {
        return parent;
    }

    @Override
    public List<AgentInstance> subagents() {
        return List.of();
    }

    @Override
    public AgenticSystemTopology topology() {
        return AgenticSystemTopology.AI_AGENT;
    }
}
