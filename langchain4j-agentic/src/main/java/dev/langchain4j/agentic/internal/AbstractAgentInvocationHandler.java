package dev.langchain4j.agentic.internal;

import dev.langchain4j.agentic.cognisphere.Cognisphere;
import dev.langchain4j.agentic.cognisphere.CognisphereAccess;
import dev.langchain4j.agentic.cognisphere.ResultWithCognisphere;
import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.service.MemoryId;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.Proxy;
import java.util.Map;

public abstract class AbstractAgentInvocationHandler implements CognisphereOwner {
    protected final String outputName;

    private final Class<?> agentServiceClass;
    private final Cognisphere cognisphere;

    protected AbstractAgentInvocationHandler(Class<?> agentServiceClass, String outputName) {
        this(agentServiceClass, outputName, null);
    }

    protected AbstractAgentInvocationHandler(Class<?> agentServiceClass, String outputName, Cognisphere cognisphere) {
        this.agentServiceClass = agentServiceClass;
        this.outputName = outputName;
        this.cognisphere = cognisphere;
    }

    @Override
    public CognisphereOwner withCognisphere(final Cognisphere cognisphere) {
        return (CognisphereOwner) Proxy.newProxyInstance(
                agentServiceClass.getClassLoader(),
                new Class<?>[] {agentServiceClass, AgentInstance.class, CognisphereOwner.class},
                createSubAgentWithCognisphere(cognisphere));
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) {
        if (method.getDeclaringClass() == CognisphereOwner.class) {
            return switch (method.getName()) {
                case "withCognisphere" -> withCognisphere((Cognisphere) args[0]);
                default ->
                        throw new UnsupportedOperationException(
                                "Unknown method on CognisphereOwner class : " + method.getName());
            };
        }

        if (method.getDeclaringClass() == CognisphereAccess.class) {
            return switch (method.getName()) {
                case "getCognisphere" -> Cognisphere.registry().get(args[0]);
                case "evictCognisphere" -> Cognisphere.registry().evict(args[0]);
                default ->
                        throw new UnsupportedOperationException(
                                "Unknown method on CognisphereAccess class : " + method.getName());
            };
        }

        if (method.getDeclaringClass() == AgentInstance.class) {
            return switch (method.getName()) {
                case "outputName" -> outputName;
                default ->
                        throw new UnsupportedOperationException(
                                "Unknown method on AgentInstance class : " + method.getName());
            };
        }

        return executeAgentMethod(currentCognisphere(method, args), method, args);
    }

    private Object executeAgentMethod(Cognisphere cognisphere, Method method, Object[] args) {
        writeCognisphereState(cognisphere, method, args);

        Object result = doAgentAction(cognisphere);
        Object output = outputName != null ? cognisphere.readState(outputName) : result;
        if (isRootCall()) {
            cognisphere.onCallEnded();
        }
        return method.getReturnType().equals(ResultWithCognisphere.class) ?
                new ResultWithCognisphere<>(cognisphere, output) :
                output;
    }

    private boolean isRootCall() {
        return this.cognisphere == null;
    }

    private static void writeCognisphereState(Cognisphere cognisphere, Method method, Object[] args) {
        if (method.getDeclaringClass() == UntypedAgent.class) {
            cognisphere.writeStates((Map<String, Object>) args[0]);
        } else {
            Parameter[] parameters = method.getParameters();
            for (int i = 0; i < parameters.length; i++) {
                int index = i;
                AgentSpecification.optionalParameterName(parameters[i])
                        .ifPresent(argName -> cognisphere.writeState(argName, args[index]) );
            }
        }
    }

    private Cognisphere currentCognisphere(Method method, Object[] args) {
        if (cognisphere != null) {
            return cognisphere;
        }

        Object memoryId = memoryId(method, args);
        return memoryId != null ?
                Cognisphere.registry().getOrCreate(memoryId) :
                Cognisphere.registry().createEphemeralCognisphere();
    }

    private Object memoryId(Method method, Object[] args) {
        Parameter[] parameters = method.getParameters();
        for (int i = 0; i < parameters.length; i++) {
            if (parameters[i].getAnnotation(MemoryId.class) != null) {
                return args[i];
            }
        }
        return null;
    }

    protected Object result(Cognisphere cognisphere, Object result) {
        if (outputName != null) {
            if (result != null) {
                cognisphere.writeState(outputName, result);
                return result;
            } else {
                return cognisphere.readState(outputName);
            }
        }
        return result;
    }

    protected abstract Object doAgentAction(Cognisphere cognisphere);

    protected abstract CognisphereOwner createSubAgentWithCognisphere(Cognisphere cognisphere);
}
