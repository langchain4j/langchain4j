package dev.langchain4j.agentic.internal;

import dev.langchain4j.agentic.agent.AgentInvocationException;
import dev.langchain4j.agentic.agent.MissingArgumentException;
import dev.langchain4j.agentic.observability.AgentListener;
import dev.langchain4j.agentic.observability.AgentListenerProvider;
import dev.langchain4j.agentic.observability.ComposedAgentListener;
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

import static dev.langchain4j.agentic.observability.ListenerNotifierUtil.afterAgentInvocation;
import static dev.langchain4j.agentic.observability.ListenerNotifierUtil.agentError;
import static dev.langchain4j.agentic.observability.ListenerNotifierUtil.beforeAgentInvocation;

public interface AgentInvoker extends AgentInstance, AgentListenerProvider {

    Method method();

    AgentInvocationArguments toInvocationArguments(AgenticScope agenticScope) throws MissingArgumentException;

    default Object invoke(DefaultAgenticScope agenticScope, Object agent, AgentInvocationArguments args) throws AgentInvocationException {
        AgentListener listener = listener(agenticScope);
        beforeAgentInvocation(listener, agenticScope, this, args.namedArgs());
        Object result = internalInvoke(agenticScope, listener, agent, args);
        afterAgentInvocation(listener, agenticScope, this, args.namedArgs(), result);
        return result;
    }

    private AgentListener listener(DefaultAgenticScope agenticScope) {
        AgentListener localListener = listener();
        if (localListener == null) {
            return agenticScope.listener();
        }
        if (agenticScope.listener() == null) {
            return localListener;
        }
        return new ComposedAgentListener(localListener, agenticScope.listener());
    }

    private Object internalInvoke(DefaultAgenticScope agenticScope, AgentListener listener, Object agent, AgentInvocationArguments args) {
        LangChain4jManaged.setCurrent(Map.of(AgenticScope.class, agenticScope));
        try {
            return method().invoke(agent, args.positionalArgs());
        } catch (Exception e) {
            AgentInvocationException invocationException = new AgentInvocationException("Failed to invoke agent method: " + method(), e);
            agentError(listener, agenticScope, this, args.namedArgs(), invocationException);
            throw invocationException;
        } finally {
            LangChain4jManaged.removeCurrent();
        }
    }

    static AgentInvoker fromSpec(AgentSpecsProvider spec, Method agenticMethod, String name, String agentId) {
        List<AgentArgument> arguments = List.of(new AgentArgument(agenticMethod.getGenericParameterTypes()[0], spec.inputKey()));
        AgentInstance agentInstance = new NonAiAgentInstance(agenticMethod.getDeclaringClass(),
                name, agentId, spec.description(), agenticMethod.getGenericReturnType(), spec.outputKey(), spec.async(), arguments,
                x -> { }, x -> { }, spec.listener());
        return new MethodAgentInvoker(agenticMethod, agentInstance);
    }

    static AgentInvoker fromMethod(AgentInstance spec, Method method) {
        if (method.getDeclaringClass() == UntypedAgent.class) {
            return new UntypedAgentInvoker(method, spec);
        }

        return new MethodAgentInvoker(method, spec);
    }

    static String parameterName(Parameter parameter) {
        return optionalParameterName(parameter)
                .orElseThrow(() -> new IllegalArgumentException("Parameter name not specified and no @V or @K annotation present: " + parameter));
    }

    static Optional<String> optionalParameterName(Parameter parameter) {
        return Optional.ofNullable(ParameterNameResolver.name(parameter));
    }
}
