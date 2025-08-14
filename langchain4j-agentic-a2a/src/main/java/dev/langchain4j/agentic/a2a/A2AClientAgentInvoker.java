package dev.langchain4j.agentic.a2a;

import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.internal.AgentInvoker;
import io.a2a.spec.AgentCard;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.stream.Stream;

public class A2AClientAgentInvoker implements AgentInvoker {

    private final String[] inputNames;
    private final String outputName;
    private final AgentCard agentCard;
    private final Method method;

    public A2AClientAgentInvoker(A2AClientSpecification a2AClientInstance, Method method) {
        this.method = method;
        this.agentCard = a2AClientInstance.agentCard();
        this.inputNames = inputNames(a2AClientInstance);
        this.outputName = a2AClientInstance.outputName();
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
    public String description() {
        return agentCard.description();
    }

    @Override
    public String outputName() {
        return outputName;
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
    public Object[] toInvocationArguments(AgenticScope agenticScope) {
        return isUntyped() ?
                new Object[] { agenticScope.state() } :
                Stream.of(inputNames).map(agenticScope::readState).toArray();
    }

    private boolean isUntyped() {
        return method.getDeclaringClass() == UntypedAgent.class;
    }
}
