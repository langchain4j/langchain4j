package dev.langchain4j.agentic.internal;

import dev.langchain4j.agentic.cognisphere.Cognisphere;
import dev.langchain4j.agentic.cognisphere.CognisphereAccess;
import dev.langchain4j.agentic.cognisphere.CognisphereKey;
import dev.langchain4j.agentic.cognisphere.CognisphereRegistry;
import dev.langchain4j.agentic.cognisphere.ResultWithCognisphere;
import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.service.MemoryId;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public abstract class AbstractAgentInvocationHandler implements CognisphereOwner {
    private static final AtomicInteger AGENTS_COUNTER = new AtomicInteger();

    protected final String outputName;

    private final Class<?> agentServiceClass;

    private final Consumer<Cognisphere> beforeCall;

    private final Cognisphere cognisphere;
    private final String agentId;

    protected AbstractAgentInvocationHandler(AbstractService<?, ?> workflowService) {
        this(workflowService, null);
    }

    protected AbstractAgentInvocationHandler(AbstractService<?, ?> workflowService, Cognisphere cognisphere) {
        this.agentServiceClass = workflowService.agentServiceClass;
        this.outputName = workflowService.outputName;
        this.beforeCall = workflowService.beforeCall;
        this.cognisphere = cognisphere;
        this.agentId = isRootCall() ? this.agentServiceClass.getSimpleName() + "@" + AGENTS_COUNTER.getAndIncrement() : null;
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
            if (agentId == null) {
                throw new IllegalStateException("CognisphereAccess methods can only be called on the root agent.");
            }
            return switch (method.getName()) {
                case "getCognisphere" -> CognisphereRegistry.get(new CognisphereKey(agentId, args[0]));
                case "evictCognisphere" -> CognisphereRegistry.evict(new CognisphereKey(agentId, args[0]));
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
        beforeCall.accept(cognisphere);

        if (isRootCall()) {
            cognisphere.rootCallStarted();
        }
        Object result = doAgentAction(cognisphere);
        if (isRootCall()) {
            cognisphere.rootCallEnded();
        }

        Object output = outputName != null ? cognisphere.readState(outputName) : result;
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
                CognisphereRegistry.getOrCreate(new CognisphereKey(agentId, memoryId)) :
                CognisphereRegistry.createEphemeralCognisphere();
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
