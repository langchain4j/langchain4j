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
import dev.langchain4j.agentic.internal.UserMessageRecorder;
import dev.langchain4j.agentic.planner.AgenticSystemConfigurationException;
import dev.langchain4j.agentic.planner.AgenticSystemTopology;
import dev.langchain4j.agentic.planner.Planner;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.agentic.scope.DefaultAgenticScope;
import dev.langchain4j.invocation.LangChain4jManaged;
import dev.langchain4j.service.AiServiceContext;
import dev.langchain4j.service.memory.ChatMemoryAccess;
import dev.langchain4j.service.memory.ChatMemoryService;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.util.List;

import static dev.langchain4j.agentic.observability.ComposedAgentListener.composeWithInherited;
import static dev.langchain4j.agentic.observability.ComposedAgentListener.listenerOfType;

public class AgentInvocationHandler implements InvocationHandler, InternalAgent {

    private final AiServiceContext context;
    private final AgentBuilder<?, ?> builder;
    private final Object agent;
    private final UserMessageRecorder messageRecorder;
    private final boolean agenticScopeDependent;
    private String agentId;
    private InternalAgent parent;
    private AgentListener agentListener;

    AgentInvocationHandler(
            AiServiceContext context,
            Object agent,
            AgentBuilder<?, ?> builder,
            UserMessageRecorder messageRecorder,
            boolean agenticScopeDependent) {
        this.context = context;
        this.agent = agent;
        this.builder = builder;
        this.agentId = builder.name;
        this.messageRecorder = messageRecorder;
        this.agenticScopeDependent = agenticScopeDependent;
        this.agentListener = builder.agentListener;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Exception {
        if (method.getDeclaringClass() == ChatMessagesAccess.class) {
            return switch (method.getName()) {
                case "lastUserMessage" -> messageRecorder.lastUserMessage();
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

        return method.invoke(agent, args);
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
                    agentListener.beforeAgentToolExecution(new BeforeAgentToolExecution(
                            (AgenticScope) LangChain4jManaged.current().get(AgenticScope.class), this, beforeToolExecution)));
            context.toolService.afterToolExecution(toolExecution ->
                    agentListener.afterAgentToolExecution(new AfterAgentToolExecution(
                            (AgenticScope) LangChain4jManaged.current().get(AgenticScope.class), this, toolExecution)));
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
