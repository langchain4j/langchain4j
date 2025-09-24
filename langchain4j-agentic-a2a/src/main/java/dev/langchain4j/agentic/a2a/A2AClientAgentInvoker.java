package dev.langchain4j.agentic.a2a;

import dev.langchain4j.agentic.agent.AgentRequest;
import dev.langchain4j.agentic.agent.AgentResponse;
import dev.langchain4j.agentic.internal.AgentInvocationArguments;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.internal.AgentInvoker;
import io.a2a.spec.AgentCard;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
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
    public void beforeInvocation(AgentRequest request) {
        a2AClientInstance.beforeInvocation(request);
    }

    @Override
    public void afterInvocation(AgentResponse response) {
        a2AClientInstance.afterInvocation(response);
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
    public AgentInvocationArguments toInvocationArguments(AgenticScope agenticScope) {
        return isUntyped() ?
                new AgentInvocationArguments(agenticScope.state(), new Object[] { agenticScope.state() }) :
                agentInvocationArguments(agenticScope);
    }

    private AgentInvocationArguments agentInvocationArguments(AgenticScope agenticScope) {
        Map<String, Object> namedArgs = new HashMap<>();
        Object[] positionalArgs = new Object[inputNames.length];

        int i = 0;
        for (String argName : inputNames) {
            Object argValue = agenticScope.readState(argName);
            positionalArgs[i++] = argValue;
            namedArgs.put(argName, argValue);
        }
        return new AgentInvocationArguments(namedArgs, positionalArgs);    }

    private boolean isUntyped() {
        return method.getDeclaringClass() == UntypedAgent.class;
    }
}
