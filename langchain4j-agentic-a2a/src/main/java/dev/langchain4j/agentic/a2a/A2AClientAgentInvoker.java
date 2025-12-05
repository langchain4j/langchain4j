package dev.langchain4j.agentic.a2a;

import static dev.langchain4j.agentic.internal.AgentUtil.uniqueAgentName;

import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.observability.AgenticListener;
import dev.langchain4j.agentic.observability.AgentListenerProvider;
import dev.langchain4j.agentic.internal.AgentInvocationArguments;
import dev.langchain4j.agentic.planner.AgentArgument;
import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.agentic.internal.AgentInvoker;
import io.a2a.spec.AgentCard;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public class A2AClientAgentInvoker implements AgentInvoker {

    private final String agentId;
    private final String[] inputKeys;

    private final A2AClientInstance a2AClientInstance;

    private final AgentCard agentCard;
    private final Method method;

    public A2AClientAgentInvoker(A2AClientInstance a2AClientInstance, Method method) {
        this.method = method;
        this.a2AClientInstance = a2AClientInstance;
        this.agentCard = a2AClientInstance.agentCard();
        this.agentId = uniqueAgentName(method.getDeclaringClass(), name());
        this.inputKeys = inputKeys(a2AClientInstance);
    }

    private String[] inputKeys(A2AClientInstance a2AClientInstance) {
        return isUntyped()
                ? a2AClientInstance.inputKeys()
                : Stream.of(method.getParameters())
                        .map(AgentInvoker::parameterName)
                        .toArray(String[]::new);
    }

    @Override
    public String name() {
        return agentCard.name();
    }

    @Override
    public String agentId() {
        return agentId;
    }

    @Override
    public String description() {
        return agentCard.description();
    }

    @Override
    public Class<?> type() {
        return Object.class;
    }

    @Override
    public Type outputType() {
        return Object.class;
    }

    @Override
    public String outputKey() {
        return a2AClientInstance.outputKey();
    }

    @Override
    public boolean async() {
        return a2AClientInstance.async();
    }

    @Override
    public Method method() {
        return method;
    }

    @Override
    public List<AgentArgument> arguments() {
        return Stream.of(inputKeys).map(input -> new AgentArgument(Object.class, input)).toList();
    }

    @Override
    public List<AgentInstance> subagents() {
        return List.of();
    }

    @Override
    public AgentInvocationArguments toInvocationArguments(AgenticScope agenticScope) {
        return isUntyped()
                ? new AgentInvocationArguments(agenticScope.state(), new Object[] {agenticScope.state()})
                : agentInvocationArguments(agenticScope);
    }

    private AgentInvocationArguments agentInvocationArguments(AgenticScope agenticScope) {
        Map<String, Object> namedArgs = new HashMap<>();
        Object[] positionalArgs = new Object[inputKeys.length];

        int i = 0;
        for (String argName : inputKeys) {
            Object argValue = agenticScope.readState(argName);
            positionalArgs[i++] = argValue;
            namedArgs.put(argName, argValue);
        }
        return new AgentInvocationArguments(namedArgs, positionalArgs);
    }

    private boolean isUntyped() {
        return method.getDeclaringClass() == UntypedAgent.class;
    }

    @Override
    public AgenticListener listener() {
        return ((AgentListenerProvider) a2AClientInstance).listener();
    }
}
