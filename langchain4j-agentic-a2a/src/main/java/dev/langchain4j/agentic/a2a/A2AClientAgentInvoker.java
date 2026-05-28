package dev.langchain4j.agentic.a2a;

import static dev.langchain4j.agentic.internal.AgentUtil.agentInvocationArguments;
import static dev.langchain4j.agentic.internal.AgentUtil.argumentsFromMethod;

import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.declarative.A2AContextId;
import dev.langchain4j.agentic.declarative.A2ATaskId;
import dev.langchain4j.agentic.internal.AgentInvocationArguments;
import dev.langchain4j.agentic.internal.AgentInvoker;
import dev.langchain4j.agentic.internal.InternalAgent;
import dev.langchain4j.agentic.observability.AgentListener;
import dev.langchain4j.agentic.planner.AgentArgument;
import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.planner.AgenticSystemTopology;
import dev.langchain4j.agentic.planner.Planner;
import dev.langchain4j.agentic.scope.AgenticScope;
import io.a2a.spec.AgentCard;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.Type;
import java.util.List;
import java.util.stream.Stream;

public class A2AClientAgentInvoker implements AgentInvoker {

    private String agentId;
    private final List<AgentArgument> arguments;

    private final A2AClientInstance a2AClientInstance;

    private final AgentCard agentCard;
    private final String contextIdKey;
    private final String taskIdKey;
    private final Method method;

    private InternalAgent parent;

    public A2AClientAgentInvoker(A2AClientInstance a2AClientInstance, Method method) {
        this.method = method;
        this.a2AClientInstance = a2AClientInstance;
        this.agentCard = a2AClientInstance.agentCard();
        this.contextIdKey = a2AClientInstance.contextIdKey();
        this.taskIdKey = a2AClientInstance.taskIdKey();
        this.agentId = name();
        this.arguments = arguments(a2AClientInstance);
    }

    private List<AgentArgument> arguments(A2AClientInstance a2AClientInstance) {
        return isUntyped()
                ? Stream.of(a2AClientInstance.inputKeys())
                        .map(input -> new AgentArgument(Object.class, input))
                        .toList()
                : argumentsFromMethod(method, parameter -> !isA2AMessageContextParameter(parameter));
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
    public Class<? extends Planner> plannerType() {
        return null;
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
        return arguments;
    }

    @Override
    public List<AgentInstance> subagents() {
        return List.of();
    }

    @Override
    public AgentInvocationArguments toInvocationArguments(AgenticScope agenticScope) {
        if (isUntyped()) {
            return new AgentInvocationArguments(agenticScope.state(), new Object[] {agenticScope.state()});
        }

        AgentInvocationArguments invocationArguments = agentInvocationArguments(agenticScope, arguments);
        if (!hasA2AMessageContextParameter()) {
            return invocationArguments;
        }

        Object[] positionalArgs = new Object[method.getParameterCount()];
        Object[] businessArgs = invocationArguments.positionalArgs();
        Parameter[] parameters = method.getParameters();

        int businessArgIndex = 0;
        for (int i = 0; i < parameters.length; i++) {
            Parameter parameter = parameters[i];
            positionalArgs[i] = isA2AMessageContextParameter(parameter)
                    ? a2aMessageContextArgument(agenticScope, parameter)
                    : businessArgs[businessArgIndex++];
        }

        return new AgentInvocationArguments(invocationArguments.namedArgs(), positionalArgs);
    }

    private boolean isUntyped() {
        return method.getDeclaringClass() == UntypedAgent.class;
    }

    @Override
    public AgentListener listener() {
        return a2AClientInstance.listener();
    }

    @Override
    public AgenticSystemTopology topology() {
        return a2AClientInstance.topology();
    }

    @Override
    public AgentInstance parent() {
        return parent;
    }

    @Override
    public void setParent(InternalAgent parent) {
        this.parent = parent;
    }

    @Override
    public void registerInheritedParentListener(AgentListener parentListener) {
        a2AClientInstance.registerInheritedParentListener(parentListener);
    }

    @Override
    public void appendId(String idSuffix) {
        this.agentId = this.agentId + idSuffix;
    }

    private boolean hasA2AMessageContextParameter() {
        return Stream.of(method.getParameters()).anyMatch(this::isA2AMessageContextParameter);
    }

    private boolean isA2AMessageContextParameter(Parameter parameter) {
        return parameter.isAnnotationPresent(A2AContextId.class)
                || parameter.isAnnotationPresent(A2ATaskId.class)
                || isConfiguredKey(contextIdKey, parameter)
                || isConfiguredKey(taskIdKey, parameter);
    }

    private static Object a2aMessageContextArgument(AgenticScope agenticScope, Parameter parameter) {
        return AgentInvoker.optionalParameterName(parameter)
                .map(agenticScope::readState)
                .orElse(null);
    }

    private static boolean isConfiguredKey(String configuredKey, Parameter parameter) {
        return AgentInvoker.optionalParameterName(parameter)
                .map(key -> configuredKey != null && !configuredKey.isBlank() && configuredKey.equals(key))
                .orElse(false);
    }
}
