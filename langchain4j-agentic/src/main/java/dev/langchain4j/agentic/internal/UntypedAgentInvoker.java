package dev.langchain4j.agentic.internal;

import dev.langchain4j.agentic.agent.AgentRequest;
import dev.langchain4j.agentic.agent.AgentResponse;
import dev.langchain4j.agentic.planner.AgentArgument;
import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.scope.AgenticScope;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.List;

public record UntypedAgentInvoker(Method method, AgentSpecification agentSpecification) implements AgentInvoker {

    @Override
    public Class<?> type() {
        return agentSpecification.type();
    }

    @Override
    public String name() {
        return agentSpecification.name();
    }

    @Override
    public String agentId() {
        return agentSpecification.agentId();
    }

    @Override
    public String description() {
        return agentSpecification.description();
    }

    @Override
    public Type outputType() {
        return method.getGenericReturnType();
    }

    @Override
    public List<AgentInstance> subagents() {
        return agentSpecification.subagents();
    }

    @Override
    public String outputKey() {
        return agentSpecification.outputKey();
    }

    @Override
    public boolean async() {
        return agentSpecification.async();
    }

    @Override
    public void beforeInvocation(final AgentRequest request) {
        agentSpecification.beforeInvocation(request);
    }

    @Override
    public void afterInvocation(final AgentResponse response) {
        agentSpecification.afterInvocation(response);
    }

    @Override
    public List<AgentArgument> arguments() {
        return List.of();
    }

    @Override
    public AgentInvocationArguments toInvocationArguments(AgenticScope agenticScope) {
        return new AgentInvocationArguments(agenticScope.state(), new Object[] {agenticScope.state()});
    }
}
