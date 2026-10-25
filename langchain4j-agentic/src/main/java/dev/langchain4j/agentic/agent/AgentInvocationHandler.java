package dev.langchain4j.agentic.agent;

import static dev.langchain4j.agentic.observability.ComposedAgentListener.composeWithInherited;
import static dev.langchain4j.agentic.observability.ComposedAgentListener.listenerOfType;
import static dev.langchain4j.agentic.observability.ListenerNotifierUtil.afterAgentInvocation;
import static dev.langchain4j.agentic.observability.ListenerNotifierUtil.agentError;
import static dev.langchain4j.agentic.observability.ListenerNotifierUtil.beforeAgentInvocation;
import static dev.langchain4j.agentic.observability.ListenerNotifierUtil.onAgentAiServiceEvent;
import static dev.langchain4j.agentic.scope.DefaultAgenticScope.ephemeralAgenticScope;

import dev.langchain4j.agentic.internal.AgenticScopeOwner;
import dev.langchain4j.agentic.internal.InternalAgent;
import dev.langchain4j.agentic.observability.AfterAgentToolExecution;
import dev.langchain4j.agentic.observability.AgentListener;
import dev.langchain4j.agentic.observability.AgentMonitor;
import dev.langchain4j.agentic.observability.BeforeAgentToolExecution;
import dev.langchain4j.agentic.observability.ComposedAgentListener;
import dev.langchain4j.agentic.observability.MonitoredAgent;
import dev.langchain4j.agentic.planner.AgentArgument;
import dev.langchain4j.agentic.planner.AgentCondition;
import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.planner.AgenticSystemConfigurationException;
import dev.langchain4j.agentic.planner.AgenticSystemTopology;
import dev.langchain4j.agentic.planner.Planner;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.agentic.scope.DefaultAgenticScope;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.invocation.LangChain4jManaged;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.observability.api.event.AiServiceCancelledEvent;
import dev.langchain4j.observability.api.event.AiServiceCompletedEvent;
import dev.langchain4j.observability.api.event.AiServiceErrorEvent;
import dev.langchain4j.observability.api.event.AiServiceEvent;
import dev.langchain4j.observability.api.event.AiServiceRequestIssuedEvent;
import dev.langchain4j.observability.api.event.AiServiceResponseReceivedEvent;
import dev.langchain4j.observability.api.event.AiServiceStartedEvent;
import dev.langchain4j.observability.api.event.InputGuardrailExecutedEvent;
import dev.langchain4j.observability.api.event.OutputGuardrailExecutedEvent;
import dev.langchain4j.observability.api.event.RetrievalAugmentedEvent;
import dev.langchain4j.observability.api.event.ToolCompensatedEvent;
import dev.langchain4j.observability.api.event.ToolExecutedEvent;
import dev.langchain4j.observability.api.listener.AiServiceListener;
import dev.langchain4j.observability.api.listener.AiServiceResponseReceivedListener;
import dev.langchain4j.service.AiServiceContext;
import dev.langchain4j.service.ParameterNameResolver;
import dev.langchain4j.service.memory.ChatMemoryAccess;
import dev.langchain4j.service.memory.ChatMemoryService;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AgentInvocationHandler implements InvocationHandler, InternalAgent {

    private static final Logger LOGGER = LoggerFactory.getLogger(AgentInvocationHandler.class);

    private static final List<Class<? extends AiServiceEvent>> AI_SERVICE_EVENT_TYPES = List.of(
            AiServiceStartedEvent.class,
            InputGuardrailExecutedEvent.class,
            RetrievalAugmentedEvent.class,
            AiServiceRequestIssuedEvent.class,
            AiServiceResponseReceivedEvent.class,
            ToolExecutedEvent.class,
            ToolCompensatedEvent.class,
            OutputGuardrailExecutedEvent.class,
            AiServiceCompletedEvent.class,
            AiServiceErrorEvent.class,
            AiServiceCancelledEvent.class);

    private final AiServiceContext context;
    private final AgentBuilder<?, ?> builder;
    private final Object agent;
    private final boolean agenticScopeDependent;
    private final Function<AgenticScope, ChatModel> chatModelProvider;
    private final Function<AgenticScope, StreamingChatModel> streamingChatModelProvider;
    private String agentId;
    private InternalAgent parent;
    private AgentListener agentListener;
    private boolean crossAgentCompensationEnabled;
    private boolean aiServiceEventsForwarded;
    private final InvokingAgent invokingAgent = new InvokingAgent(this);

    private record InvokingAgent(AgentInvocationHandler agent) implements LangChain4jManaged {}

    private final Map<Object, LastResponse> lastResponses = new ConcurrentHashMap<>();

    private record LastResponse(AiServiceResponseReceivedEvent event, TokenUsage tokenUsage) {

        static LastResponse first(AiServiceResponseReceivedEvent event) {
            return new LastResponse(event, event.response().metadata().tokenUsage());
        }

        LastResponse next(AiServiceResponseReceivedEvent nextEvent) {
            if (!Objects.equals(
                    event.invocationContext().invocationId(),
                    nextEvent.invocationContext().invocationId())) {
                return first(nextEvent);
            }
            return new LastResponse(
                    nextEvent,
                    TokenUsage.sum(tokenUsage, nextEvent.response().metadata().tokenUsage()));
        }

        ChatResponse chatResponse() {
            ChatResponse response = event.response();
            return ChatResponse.builder()
                    .aiMessage(response.aiMessage())
                    .metadata(response.metadata().toBuilder()
                            .tokenUsage(tokenUsage)
                            .build())
                    .build();
        }
    }

    public AgentInvocationHandler(
            AiServiceContext context, Object agent, AgentBuilder<?, ?> builder, boolean agenticScopeDependent) {
        this.context = context;
        this.agent = agent;
        this.builder = builder;
        this.agentId = builder.name;
        this.agenticScopeDependent = agenticScopeDependent;
        this.agentListener = builder.agentListener;
        this.chatModelProvider = builder.chatModelProvider;
        this.streamingChatModelProvider = builder.streamingChatModelProvider;
        forwardAiServiceEvents();
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Exception {
        if (method.getDeclaringClass() == AiServiceListener.class
                || method.getDeclaringClass() == AiServiceResponseReceivedListener.class) {
            return switch (method.getName()) {
                case "getEventClass" -> AiServiceResponseReceivedEvent.class;
                case "onEvent" -> {
                    AiServiceResponseReceivedEvent event = (AiServiceResponseReceivedEvent) args[0];
                    AgenticScope agenticScope = (AgenticScope)
                            event.invocationContext().managedParameters().get(AgenticScope.class);
                    lastResponses.compute(
                            agenticScope.memoryId(),
                            (memoryId, previous) ->
                                    previous == null ? LastResponse.first(event) : previous.next(event));
                    yield null;
                }
                default ->
                    throw new UnsupportedOperationException(
                            "Unknown method on AiServiceResponseReceivedListener class : " + method.getName());
            };
        }

        if (method.getDeclaringClass() == ChatMessagesAccess.class) {
            if ("removeLastResponseEvent".equals(method.getName())) {
                lastResponses.remove(args[0]);
                return null;
            }
            LastResponse lastResponse = lastResponses.get(args[0]);
            if (lastResponse == null) {
                return null;
            }
            return switch (method.getName()) {
                case "lastUserMessage" ->
                    lastUserMessage(lastResponse.event().request().messages()).orElse(null);
                case "lastChatRequest" -> lastResponse.event().request();
                case "lastChatResponse" -> lastResponse.chatResponse();
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
                    ((InternalAgent) agentProxy).setParent(parent);
                    ((InternalAgent) agentProxy).setAgentId(agentId);
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
                    context.hasChatMemory()
                                    && (ChatMemoryService.DEFAULT.equals(args[0]) || builder.hasNonDefaultChatMemory())
                            ? context.chatMemoryService.getChatMemory(args[0])
                            : null;
                case "evictChatMemory" ->
                    context.hasChatMemory() && context.chatMemoryService.evictChatMemory(args[0]) != null;
                default ->
                    throw new UnsupportedOperationException(
                            "Unknown method on ChatMemoryAccess class : " + method.getName());
            };
        }

        if (method.getDeclaringClass() == AgentInstance.class || method.getDeclaringClass() == InternalAgent.class) {
            try {
                return method.invoke(this, args);
            } catch (Exception e) {
                throw e.getCause() != null ? (Exception) e.getCause() : e;
            }
        }

        if (method.getDeclaringClass() == MonitoredAgent.class) {
            return listenerOfType(agentListener, AgentMonitor.class);
        }

        if (method.getDeclaringClass() == Object.class) {
            return switch (method.getName()) {
                case "equals" -> proxy == args[0];
                case "toString" -> "Agent<" + builder.agentServiceClass.getSimpleName() + ">";
                case "hashCode" -> System.identityHashCode(agent);
                default ->
                    throw new UnsupportedOperationException("Unknown method on Object class : " + method.getName());
            };
        }

        AgenticScope agenticScope = LangChain4jManaged.current(AgenticScope.class);
        if (agenticScope != null) {
            if (chatModelProvider != null) {
                context.chatModel = chatModelProvider.apply(agenticScope);
            } else if (streamingChatModelProvider != null) {
                context.streamingChatModel = streamingChatModelProvider.apply(agenticScope);
            }
            try {
                return invokeAiService(method, args);
            } catch (Exception e) {
                lastResponses.remove(agenticScope.memoryId());
                throw e;
            }
        }
        return invokeStandaloneAgent(method, args);
    }

    private Object invokeAiService(Method method, Object[] args) throws Exception {
        if (!aiServiceEventsForwarded) {
            return method.invoke(agent, args);
        }
        Map<Class<? extends LangChain4jManaged>, LangChain4jManaged> current = LangChain4jManaged.current();
        Map<Class<? extends LangChain4jManaged>, LangChain4jManaged> withInvokingAgent = new HashMap<>(current);
        withInvokingAgent.put(InvokingAgent.class, invokingAgent);
        LangChain4jManaged.setCurrent(withInvokingAgent);
        try {
            return method.invoke(agent, args);
        } finally {
            LangChain4jManaged.setCurrent(current);
        }
    }

    private Object invokeStandaloneAgent(Method method, Object[] args) {
        LOGGER.warn(
                "Improper invocation of a standalone agent outside of an agentic system, consider using AiServices instead.");

        AgenticScope standaloneAgenticScope = ephemeralAgenticScope();
        LangChain4jManaged.setCurrent(Map.of(AgenticScope.class, standaloneAgenticScope));

        Map<String, Object> namedArgs = argToMap(method, args);
        beforeAgentInvocation(agentListener, standaloneAgenticScope, this, namedArgs);

        Object result = null;
        try {
            result = invokeAiService(method, args);
        } catch (Exception e) {
            AgentInvocationException invocationException =
                    new AgentInvocationException("Failed to invoke agent method: " + method, e);
            agentError(agentListener, standaloneAgenticScope, this, namedArgs, invocationException);
            throw invocationException;
        } finally {
            LangChain4jManaged.removeCurrent();
            LastResponse lastResponse = lastResponses.remove(standaloneAgenticScope.memoryId());
            if (result != null) {
                afterAgentInvocation(
                        agentListener,
                        standaloneAgenticScope,
                        this,
                        namedArgs,
                        result,
                        lastResponse == null ? null : lastResponse.event().request(),
                        lastResponse == null ? null : lastResponse.chatResponse());
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
    public boolean compensateOnError() {
        return builder.compensateOnError || (parent != null && parent.compensateOnError());
    }

    @Override
    public void setParent(InternalAgent parent) {
        if (builder.hasChatMemory() && parent != null && !parent.allowChatMemory()) {
            throw new AgenticSystemConfigurationException(
                    "Agents with chat memory can't be a subagent of " + parent.type());
        }
        this.parent = parent;
        if (parent != null) {
            registerInheritedParentListener(parent.listener());
        }
        if (compensateOnError()) {
            enableCrossAgentCompensation();
        }
    }

    @Override
    public void enableCrossAgentCompensation() {
        if (crossAgentCompensationEnabled) {
            return;
        }
        crossAgentCompensationEnabled = true;
        context.toolService.onCompensableToolExecution((toolExecution, compensatingAction) -> {
            var managed = toolExecution.invocationContext().managedParameters();
            if (managed != null) {
                var scopeManaged = managed.get(AgenticScope.class);
                if (scopeManaged instanceof DefaultAgenticScope das) {
                    das.registerCompensableExecution(toolExecution, compensatingAction);
                }
            }
        });

        context.toolService.onToolExecutionError(invocationContext -> {
            var managed = invocationContext.managedParameters();
            if (managed != null) {
                var scopeManaged = managed.get(AgenticScope.class);
                if (scopeManaged instanceof DefaultAgenticScope das) {
                    das.compensateAll();
                }
            }
        });
    }

    @Override
    public void registerInheritedParentListener(AgentListener parentListener) {
        if (parentListener != null
                && parentListener.inheritedBySubagents()
                && isNewListener(agentListener, parentListener)) {
            agentListener = composeWithInherited(agentListener, parentListener);
            context.toolService.beforeToolExecution(beforeToolExecution ->
                    agentListener.beforeAgentToolExecution(new BeforeAgentToolExecution(this, beforeToolExecution)));
            context.toolService.afterToolExecution(toolExecution ->
                    agentListener.afterAgentToolExecution(new AfterAgentToolExecution(this, toolExecution)));
            forwardAiServiceEvents();
        }
    }

    private void forwardAiServiceEvents() {
        if (agentListener == null || aiServiceEventsForwarded) {
            return;
        }
        aiServiceEventsForwarded = true;
        AI_SERVICE_EVENT_TYPES.forEach(
                eventType -> context.eventListenerRegistrar.register(aiServiceEventForwarder(eventType)));
    }

    private <E extends AiServiceEvent> AiServiceListener<E> aiServiceEventForwarder(Class<E> eventType) {
        return new AiServiceListener<>() {
            @Override
            public Class<E> getEventClass() {
                return eventType;
            }

            @Override
            public void onEvent(E event) {
                Map<Class<? extends LangChain4jManaged>, LangChain4jManaged> managed =
                        event.invocationContext().managedParameters();
                if (managed != null && managed.get(InvokingAgent.class) == invokingAgent) {
                    onAgentAiServiceEvent(agentListener, AgentInvocationHandler.this, event);
                }
            }
        };
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
    public void setAgentId(String agentId) {
        this.agentId = agentId;
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
    public AgentCondition activationCondition() {
        return builder.activationCondition;
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
