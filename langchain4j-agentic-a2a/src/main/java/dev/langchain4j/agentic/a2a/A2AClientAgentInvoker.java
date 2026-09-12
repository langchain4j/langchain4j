package dev.langchain4j.agentic.a2a;

import static dev.langchain4j.agentic.internal.AgentUtil.agentInvocationArguments;
import static dev.langchain4j.agentic.internal.AgentUtil.argumentsFromMethod;
import static dev.langchain4j.agentic.internal.AgentUtil.untypedAgentInvocationArguments;

import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.internal.AgentInvocationArguments;
import dev.langchain4j.agentic.internal.AgentInvoker;
import dev.langchain4j.agentic.internal.InternalAgent;
import dev.langchain4j.agentic.observability.AgentListener;
import dev.langchain4j.agentic.planner.AgentArgument;
import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.planner.AgenticSystemTopology;
import dev.langchain4j.agentic.planner.Planner;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.service.ParameterNameResolver;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.a2aproject.sdk.spec.AgentCard;

public class A2AClientAgentInvoker implements AgentInvoker {

    private String agentId;
    private final List<AgentArgument> arguments;

    private final A2AClientInstance a2AClientInstance;

    private final AgentCard agentCard;
    private final Method method;

    private InternalAgent parent;

    public A2AClientAgentInvoker(A2AClientInstance a2AClientInstance, Method method) {
        this.method = method;
        this.a2AClientInstance = a2AClientInstance;
        this.agentCard = a2AClientInstance.agentCard();
        this.agentId = name();
        this.arguments = arguments(a2AClientInstance);
    }

    private List<AgentArgument> arguments(A2AClientInstance a2AClientInstance) {
        if (isUntyped()) {
            return Stream.of(a2AClientInstance.inputKeys())
                    .map(input -> new AgentArgument(Object.class, input))
                    .toList();
        }
        Set<String> optionalProtocolArgs = Stream.of(method.getParameters())
                .filter(p -> (p.isAnnotationPresent(A2AContextId.class)
                                || p.isAnnotationPresent(A2ATaskId.class)
                                || p.isAnnotationPresent(A2ATenantId.class))
                        && ParameterNameResolver.hasName(p))
                .map(ParameterNameResolver::name)
                .collect(Collectors.toSet());
        return argumentsFromMethod(method, optionalProtocolArgs);
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
        return isUntyped()
                ? untypedAgentInvocationArguments(agenticScope)
                : agentInvocationArguments(agenticScope, arguments);
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
}
