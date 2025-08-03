package dev.langchain4j.agentic.internal;

import dev.langchain4j.agentic.cognisphere.Cognisphere;
import dev.langchain4j.agentic.cognisphere.DefaultCognisphere;
import dev.langchain4j.agentic.cognisphere.CognisphereAccess;
import dev.langchain4j.agentic.cognisphere.CognisphereRegistry;
import dev.langchain4j.agentic.cognisphere.ResultWithCognisphere;
import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.service.MemoryId;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public abstract class AbstractAgentInvocationHandler implements InvocationHandler {
    protected final String outputName;

    private final Class<?> agentServiceClass;

    private final Consumer<Cognisphere> beforeCall;

    private final DefaultCognisphere cognisphere;

    private final AtomicReference<CognisphereRegistry> cognisphereRegistry = new AtomicReference<>();

    protected AbstractAgentInvocationHandler(AbstractService<?, ?> workflowService) {
        this(workflowService, null);
    }

    protected AbstractAgentInvocationHandler(AbstractService<?, ?> workflowService, DefaultCognisphere cognisphere) {
        this.agentServiceClass = workflowService.agentServiceClass;
        this.outputName = workflowService.outputName;
        this.beforeCall = workflowService.beforeCall;
        this.cognisphere = cognisphere;
    }

    public CognisphereOwner withCognisphere(DefaultCognisphere cognisphere) {
        return (CognisphereOwner) Proxy.newProxyInstance(
                agentServiceClass.getClassLoader(),
                new Class<?>[] {agentServiceClass, AgentInstance.class, CognisphereOwner.class},
                createSubAgentWithCognisphere(cognisphere));
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) {
        CognisphereRegistry registry = cognisphereRegistry();
        if (method.getDeclaringClass() == CognisphereOwner.class) {
            return switch (method.getName()) {
                case "withCognisphere" -> withCognisphere((DefaultCognisphere) args[0]);
                case "registry" -> registry;
                default -> throw new UnsupportedOperationException(
                        "Unknown method on CognisphereOwner class : " + method.getName());
            };
        }

        if (method.getDeclaringClass() == CognisphereAccess.class) {
            return switch (method.getName()) {
                case "getCognisphere" -> registry.get(args[0]);
                case "evictCognisphere" -> registry.evict(args[0]);
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

        return executeAgentMethod(currentCognisphere(registry, method, args), registry, method, args);
    }

    private CognisphereRegistry cognisphereRegistry() {
        if (isRootCall()) {
            cognisphereRegistry.compareAndSet(null, new CognisphereRegistry(this.agentServiceClass.getName()));
        }
        return cognisphereRegistry.get();
    }

    private Object executeAgentMethod(DefaultCognisphere cognisphere, CognisphereRegistry registry, Method method, Object[] args) {
        writeCognisphereState(cognisphere, method, args);
        beforeCall.accept(cognisphere);

        if (isRootCall()) {
            cognisphere.rootCallStarted(registry);
        }
        Object result = doAgentAction(cognisphere);
        if (isRootCall()) {
            cognisphere.rootCallEnded(registry);
        }

        Object output = outputName != null ? cognisphere.readState(outputName) : result;
        return method.getReturnType().equals(ResultWithCognisphere.class) ?
                new ResultWithCognisphere<>(cognisphere, output) :
                output;
    }

    private boolean isRootCall() {
        return this.cognisphere == null;
    }

    private static void writeCognisphereState(DefaultCognisphere cognisphere, Method method, Object[] args) {
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

    private DefaultCognisphere currentCognisphere(CognisphereRegistry registry, Method method, Object[] args) {
        if (cognisphere != null) {
            return cognisphere;
        }

        Object memoryId = memoryId(method, args);
        return memoryId != null ? registry.getOrCreate(memoryId) : registry.createEphemeralCognisphere();
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

    protected Object result(DefaultCognisphere cognisphere, Object result) {
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

    protected abstract Object doAgentAction(DefaultCognisphere cognisphere);

    protected abstract InvocationHandler createSubAgentWithCognisphere(DefaultCognisphere cognisphere);
}
