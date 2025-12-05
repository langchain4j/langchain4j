package dev.langchain4j.agentic.internal;

import dev.langchain4j.agentic.observability.AgenticListener;
import dev.langchain4j.agentic.observability.AgentListenerProvider;
import dev.langchain4j.agentic.planner.AgentArgument;
import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.scope.AgenticScope;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.List;

public record UntypedAgentInvoker(Method method, AgentInstance agentInstance) implements AgentInvoker {

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
        return method.getGenericReturnType();
    }

    @Override
    public List<AgentInstance> subagents() {
        return agentInstance.subagents();
    }

    @Override
    public String outputKey() {
        return agentInstance.outputKey();
    }

    @Override
    public boolean async() {
        return agentInstance.async();
    }

    @Override
    public List<AgentArgument> arguments() {
        return List.of();
    }

    @Override
    public AgentInvocationArguments toInvocationArguments(AgenticScope agenticScope) {
        return new AgentInvocationArguments(agenticScope.state(), new Object[] {agenticScope.state()});
    }

    @Override
    public AgenticListener listener() {
        return ((AgentListenerProvider) agentInstance).listener();
    }
}
