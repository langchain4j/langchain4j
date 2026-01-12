package dev.langchain4j.agentic.internal;

import dev.langchain4j.agentic.agent.AgentInvocationException;
import dev.langchain4j.agentic.agent.MissingArgumentException;
import dev.langchain4j.agentic.observability.AgentListener;
import dev.langchain4j.agentic.planner.AgentArgument;
import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.scope.DefaultAgenticScope;
import dev.langchain4j.invocation.LangChain4jManaged;
import dev.langchain4j.service.ParameterNameResolver;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static dev.langchain4j.agentic.observability.ComposedAgentListener.composeWithInherited;
import static dev.langchain4j.agentic.observability.ListenerNotifierUtil.afterAgentInvocation;
import static dev.langchain4j.agentic.observability.ListenerNotifierUtil.agentError;
import static dev.langchain4j.agentic.observability.ListenerNotifierUtil.beforeAgentInvocation;

public interface AgentInvoker extends AgentInstance, InternalAgent {

    Method method();

    AgentInvocationArguments toInvocationArguments(AgenticScope agenticScope) throws MissingArgumentException;

    default Object invoke(DefaultAgenticScope agenticScope, Object agent, AgentInvocationArguments args) throws AgentInvocationException {
        AgentListener listener = composeWithInherited(listener(), agenticScope.listener());
        beforeAgentInvocation(listener, agenticScope, this, args.namedArgs());
        Object result = internalInvoke(agenticScope, listener, agent, args);
        afterAgentInvocation(listener, agenticScope, this, args.namedArgs(), result);
        return result;
    }

    private Object internalInvoke(DefaultAgenticScope agenticScope, AgentListener listener, Object agent, AgentInvocationArguments args) {
        LangChain4jManaged.setCurrent(Map.of(AgenticScope.class, agenticScope));
        AgentListener higherLevelListener = leaf() ? null : agenticScope.replaceListener(listener);
        try {
            return method().invoke(agent, args.positionalArgs());
        } catch (Exception e) {
            AgentInvocationException invocationException = new AgentInvocationException("Failed to invoke agent method: " + method(), e);
            agentError(listener, agenticScope, this, args.namedArgs(), invocationException);
            throw invocationException;
        } finally {
            if (!leaf()) {
                agenticScope.setListener(higherLevelListener);
            }
            LangChain4jManaged.removeCurrent();
        }
    }

    static AgentInvoker fromSpec(AgentSpecsProvider spec, Method agenticMethod, String name) {
        List<AgentArgument> arguments = List.of(new AgentArgument(agenticMethod.getGenericParameterTypes()[0], spec.inputKey()));
        InternalAgent agentInstance = new NonAiAgentInstance(agenticMethod.getDeclaringClass(),
                name, spec.description(), agenticMethod.getGenericReturnType(), spec.outputKey(), spec.async(), arguments,
                spec.listener());
        return new MethodAgentInvoker(agenticMethod, agentInstance);
    }

    static AgentInvoker fromMethod(InternalAgent agent, Method method) {
        if (method.getDeclaringClass() == UntypedAgent.class) {
            return new UntypedAgentInvoker(method, agent);
        }

        return new MethodAgentInvoker(method, agent);
    }

    static String parameterName(Parameter parameter) {
        return optionalParameterName(parameter)
                .orElseThrow(() -> new IllegalArgumentException("Parameter name not specified and no @V or @K annotation present: " + parameter));
    }

    static Optional<String> optionalParameterName(Parameter parameter) {
        return Optional.ofNullable(ParameterNameResolver.name(parameter));
    }
}
