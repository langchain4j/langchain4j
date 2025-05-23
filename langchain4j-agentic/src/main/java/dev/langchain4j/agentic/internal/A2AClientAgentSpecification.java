package dev.langchain4j.agentic.internal;

import dev.langchain4j.agentic.Cognisphere;
import dev.langchain4j.agentic.UntypedAgent;
import io.a2a.spec.AgentCard;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.stream.Stream;

public class A2AClientAgentSpecification implements AgentSpecification {

    private final String[] inputNames;
    private final AgentCard agentCard;
    private final Method method;

    public A2AClientAgentSpecification(A2AClientInstance a2AClientInstance, Method method) {
        this.method = method;
        this.agentCard = a2AClientInstance.agentCard();
        this.inputNames = inputNames(a2AClientInstance);
    }

    private String[] inputNames(A2AClientInstance a2AClientInstance) {
        return isUntyped() ?
                a2AClientInstance.inputNames() :
                Stream.of(method.getParameters()).map(AgentSpecification::parameterName).toArray(String[]::new);
    }

    @Override
    public String name() {
        return agentCard.name();
    }

    @Override
    public String description() {
        return agentCard.description();
    }

    @Override
    public Method method() {
        return method;
    }

    @Override
    public String toCard() {
        return "{" + name() + ": " + description() + ", " + Arrays.toString(inputNames) + "}";
    }

    @Override
    public Object[] toInvocationArguments(Cognisphere cognisphere) {
        return isUntyped() ?
                new Object[] { cognisphere.getState() } :
                Stream.of(inputNames).map(cognisphere::readState).toArray();
    }

    private boolean isUntyped() {
        return method.getDeclaringClass() == UntypedAgent.class;
    }
}
