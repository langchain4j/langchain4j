package dev.langchain4j.agentic.a2a;

import dev.langchain4j.agentic.agent.AgentRequest;
import dev.langchain4j.agentic.agent.AgentResponse;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.internal.AgentInvoker;
import io.a2a.spec.AgentCard;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static dev.langchain4j.agentic.internal.AgentUtil.uniqueAgentName;

public class A2AClientAgentInvoker implements AgentInvoker {

    private final String uniqueName;
    private final String[] inputNames;

    private final A2AClientSpecification a2AClientInstance;

    private final AgentCard agentCard;
    private final Method method;

    public A2AClientAgentInvoker(A2AClientSpecification a2AClientInstance, Method method) {
        this.method = method;
        this.a2AClientInstance = a2AClientInstance;
        this.agentCard = a2AClientInstance.agentCard();
        this.uniqueName = uniqueAgentName(name());
        this.inputNames = inputNames(a2AClientInstance);
    }

    private String[] inputNames(A2AClientSpecification a2AClientInstance) {
        return isUntyped() ?
                a2AClientInstance.inputNames() :
                Stream.of(method.getParameters()).map(AgentInvoker::parameterName).toArray(String[]::new);
    }

    @Override
    public String name() {
        return agentCard.name();
    }

    @Override
    public String uniqueName() {
        return uniqueName;
    }

    @Override
    public String description() {
        return agentCard.description();
    }

    @Override
    public String outputName() {
        return a2AClientInstance.outputName();
    }

    @Override
    public boolean async() {
        return a2AClientInstance.async();
    }

    @Override
    public void onInvocation(AgentRequest request) {
        a2AClientInstance.onInvocation(request);
    }

    @Override
    public void onCompletion(AgentResponse response) {
        a2AClientInstance.onCompletion(response);
    }

    @Override
    public Method method() {
        return method;
    }

    @Override
    public String toCard() {
        return "{" + uniqueName() + ": " + description() + ", " + Arrays.toString(inputNames) + "}";
    }

    @Override
    public Object[] toInvocationArguments(AgenticScope agenticScope) {
        return isUntyped() ?
                new Object[] { agenticScope.state() } :
                Stream.of(inputNames).map(agenticScope::readState).toArray();
    }

    private boolean isUntyped() {
        return method.getDeclaringClass() == UntypedAgent.class;
    }
}
