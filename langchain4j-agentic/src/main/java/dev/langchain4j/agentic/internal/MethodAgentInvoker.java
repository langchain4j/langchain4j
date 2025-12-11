package dev.langchain4j.agentic.internal;

import dev.langchain4j.agentic.agent.AgentInvocationException;
import dev.langchain4j.agentic.agent.MissingArgumentException;
import dev.langchain4j.agentic.observability.AgentListener;
import dev.langchain4j.agentic.observability.AgentListenerProvider;
import dev.langchain4j.agentic.planner.AgentArgument;
import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.planner.AgenticSystemTopology;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.agentic.scope.DefaultAgenticScope;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.List;

public record MethodAgentInvoker(Method method, AgentInstance agentInstance) implements AgentInvoker {

    @Override
    public Class<?> type() {
        return agentInstance.type();
    }

    @Override
    public String name() {
        return agentInstance.name();
    }

    @Override
    public String agentId() {
        return agentInstance.agentId();
    }

    @Override
    public String description() {
        return agentInstance.description();
    }

    @Override
    public Type outputType() {
        return agentInstance.outputType();
    }

    @Override
    public String outputKey() {
        return agentInstance.outputKey();
    }

    @Override
    public List<AgentArgument> arguments() {
        return agentInstance.arguments();
    }

    @Override
    public List<AgentInstance> subagents() {
        return agentInstance.subagents();
    }

    @Override
    public boolean async() {
        return agentInstance.async();
    }

    @Override
    public AgentInvocationArguments toInvocationArguments(AgenticScope agenticScope) throws MissingArgumentException {
        return AgentUtil.agentInvocationArguments(agenticScope, arguments());
    }

    @Override
    public Object invoke(final DefaultAgenticScope agenticScope, final Object agent, final AgentInvocationArguments args) throws AgentInvocationException {
        return AgentInvoker.super.invoke(agenticScope, agent, args);
    }

    @Override
    public AgentListener listener() {
        return ((AgentListenerProvider) agentInstance).listener();
    }

    @Override
    public AgenticSystemTopology topology() {
        return agentInstance.topology();
    }
}
