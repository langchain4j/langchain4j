package dev.langchain4j.agentic.internal;

import dev.langchain4j.agentic.agent.AgentInvocationException;
import dev.langchain4j.agentic.agent.MissingArgumentException;
import dev.langchain4j.agentic.observability.AgenticListener;
import dev.langchain4j.agentic.observability.AgentListenerProvider;
import dev.langchain4j.agentic.observability.ComposedAgenticListener;
import dev.langchain4j.agentic.planner.AgentArgument;
import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.scope.DefaultAgenticScope;
import dev.langchain4j.invocation.LangChain4jManaged;
import dev.langchain4j.service.ParameterNameResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static dev.langchain4j.agentic.observability.ListenerNotifierUtil.afterAgentInvocation;
import static dev.langchain4j.agentic.observability.ListenerNotifierUtil.agentError;
import static dev.langchain4j.agentic.observability.ListenerNotifierUtil.beforeAgentInvocation;

public interface AgentInvoker extends AgentInstance, AgentListenerProvider {

    Logger LOG = LoggerFactory.getLogger(AgentInvoker.class);

    Method method();

    AgentInvocationArguments toInvocationArguments(AgenticScope agenticScope) throws MissingArgumentException;

    default Object invoke(DefaultAgenticScope agenticScope, Object agent, AgentInvocationArguments args) throws AgentInvocationException {
        AgenticListener listener = listener(agenticScope);

        try {
            beforeAgentInvocation(listener, agenticScope, this, args.namedArgs());
        } catch (Exception e) {
            LOG.error("Before agent invocation listener for agent " + agentId() + " failed: " + e.getMessage(), e);
        }

        Object result = internalInvoke(agenticScope, listener, agent, args);

        try {
            afterAgentInvocation(listener, agenticScope, this, args.namedArgs(), result);
        } catch (Exception e) {
            LOG.error("After agent invocation listener for agent " + name() + " failed: " + e.getMessage(), e);
        }

        return result;
    }

    private AgenticListener listener(DefaultAgenticScope agenticScope) {
        AgenticListener localListener = listener();
        if (localListener == null) {
            return agenticScope.listener();
        }
        if (agenticScope.listener() == null) {
            return localListener;
        }
        return new ComposedAgenticListener(localListener, agenticScope.listener());
    }

    private Object internalInvoke(DefaultAgenticScope agenticScope, AgenticListener listener, Object agent, AgentInvocationArguments args) {
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
