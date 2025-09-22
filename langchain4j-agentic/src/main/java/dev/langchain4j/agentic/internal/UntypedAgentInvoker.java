package dev.langchain4j.agentic.internal;

import dev.langchain4j.agentic.agent.AgentRequest;
import dev.langchain4j.agentic.agent.AgentResponse;
import dev.langchain4j.agentic.scope.AgenticScope;
import java.lang.reflect.Method;
import java.util.Map;

public record UntypedAgentInvoker(Method method, AgentSpecification agentSpecification) implements AgentInvoker {

    @Override
    public String name() {
        return agentSpecification.name();
    }

    @Override
    public String uniqueName() {
        return agentSpecification.uniqueName();
    }

    @Override
    public String description() {
        return agentSpecification.description();
    }

    @Override
    public String outputName() {
        return agentSpecification.outputName();
    }

    @Override
    public boolean async() {
        return agentSpecification.async();
    }

    @Override
    public void onInvocation(final AgentRequest request) {
        agentSpecification.onInvocation(request);
    }

    @Override
    public void onCompletion(final AgentResponse response) {
        agentSpecification.onCompletion(response);
    }

    @Override
    public String toCard() {
        return "{" + uniqueName() + ": " + description() + "}";
    }

    @Override
    public AgentInvocationArguments toInvocationArguments(AgenticScope agenticScope) {
        return new AgentInvocationArguments(agenticScope.state(), new Object[] { agenticScope.state() });
    }
}
