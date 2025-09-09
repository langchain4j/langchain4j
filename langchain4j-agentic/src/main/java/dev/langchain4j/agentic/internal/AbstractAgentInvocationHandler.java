package dev.langchain4j.agentic.internal;

import dev.langchain4j.agentic.agent.ErrorContext;
import dev.langchain4j.agentic.agent.ErrorRecoveryResult;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.agentic.scope.DefaultAgenticScope;
import dev.langchain4j.agentic.scope.AgenticScopeAccess;
import dev.langchain4j.agentic.scope.AgenticScopeRegistry;
import dev.langchain4j.agentic.scope.ResultWithAgenticScope;
import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.service.MemoryId;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

public abstract class AbstractAgentInvocationHandler implements InvocationHandler {
    protected String name;
    protected String description;
    protected final String outputName;

    private final Class<?> agentServiceClass;

    private final Consumer<AgenticScope> beforeCall;

    private final DefaultAgenticScope agenticScope;

    private final Function<ErrorContext, ErrorRecoveryResult> errorHandler;

    private final AtomicReference<AgenticScopeRegistry> agenticScopeRegistry = new AtomicReference<>();

    protected AbstractAgentInvocationHandler(AbstractService<?, ?> workflowService) {
        this(workflowService, null);
    }

    protected AbstractAgentInvocationHandler(AbstractService<?, ?> service, DefaultAgenticScope agenticScope) {
        this.agentServiceClass = service.agentServiceClass;
        this.name = service.name;
        this.description = service.description;
        this.outputName = service.outputName;
        this.beforeCall = service.beforeCall;
        this.errorHandler = service.errorHandler;
        this.agenticScope = agenticScope;
    }

    public AgenticScopeOwner withAgenticScope(DefaultAgenticScope agenticScope) {
        return (AgenticScopeOwner) Proxy.newProxyInstance(
                agentServiceClass.getClassLoader(),
                new Class<?>[] {agentServiceClass, AgentSpecification.class, AgenticScopeOwner.class},
                createSubAgentWithAgenticScope(agenticScope));
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) {
        AgenticScopeRegistry registry = agenticScopeRegistry();
        if (method.getDeclaringClass() == AgenticScopeOwner.class) {
            return switch (method.getName()) {
                case "withAgenticScope" -> withAgenticScope((DefaultAgenticScope) args[0]);
                case "registry" -> registry;
                default -> throw new UnsupportedOperationException(
                        "Unknown method on AgenticScopeOwner class : " + method.getName());
            };
        }

        if (method.getDeclaringClass() == AgenticScopeAccess.class) {
            return switch (method.getName()) {
                case "getAgenticScope" -> registry.get(args[0]);
                case "evictAgenticScope" -> registry.evict(args[0]);
                default ->
                        throw new UnsupportedOperationException(
                                "Unknown method on AgenticScopeAccess class : " + method.getName());
            };
        }

        if (method.getDeclaringClass() == AgentSpecification.class) {
            return switch (method.getName()) {
                case "name" -> name;
                case "description" -> description;
                case "outputName" -> outputName;
                case "async" -> false;
                default ->
                        throw new UnsupportedOperationException(
                                "Unknown method on AgentInstance class : " + method.getName());
            };
        }

        return executeAgentMethod(currentAgenticScope(registry, method, args), registry, method, args);
    }

    private AgenticScopeRegistry agenticScopeRegistry() {
        if (isRootCall()) {
            agenticScopeRegistry.compareAndSet(null, new AgenticScopeRegistry(this.agentServiceClass.getName()));
        }
        return agenticScopeRegistry.get();
    }

    private Object executeAgentMethod(DefaultAgenticScope agenticScope, AgenticScopeRegistry registry, Method method, Object[] args) {
        writeAgenticScope(agenticScope, method, args);
        beforeCall.accept(agenticScope);

        if (isRootCall()) {
            agenticScope.rootCallStarted(registry);
        }
        Object result = doAgentAction(agenticScope);
        if (isRootCall()) {
            agenticScope.rootCallEnded(registry);
        }

        Object output = outputName != null ? agenticScope.readState(outputName) : result;
        return method.getReturnType().equals(ResultWithAgenticScope.class) ?
                new ResultWithAgenticScope<>(agenticScope, output) :
                output;
    }

    private boolean isRootCall() {
        return this.agenticScope == null;
    }

    private static void writeAgenticScope(DefaultAgenticScope agenticScope, Method method, Object[] args) {
        if (method.getDeclaringClass() == UntypedAgent.class) {
            agenticScope.writeStates((Map<String, Object>) args[0]);
        } else {
            Parameter[] parameters = method.getParameters();
            for (int i = 0; i < parameters.length; i++) {
                int index = i;
                AgentInvoker.optionalParameterName(parameters[i])
                        .ifPresent(argName -> agenticScope.writeState(argName, args[index]) );
            }
        }
    }

    private DefaultAgenticScope currentAgenticScope(AgenticScopeRegistry registry, Method method, Object[] args) {
        if (agenticScope != null) {
            return agenticScope;
        }

        Object memoryId = memoryId(method, args);
        DefaultAgenticScope newAgenticScope = memoryId != null ? registry.getOrCreate(memoryId) : registry.createEphemeralAgenticScope();
        return newAgenticScope.withErrorHandler(errorHandler);
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

    protected Object result(DefaultAgenticScope agenticScope, Object result) {
        if (outputName != null) {
            if (result != null) {
                agenticScope.writeState(outputName, result);
                return result;
            } else {
                return agenticScope.readState(outputName);
            }
        }
        return result;
    }

    protected abstract Object doAgentAction(DefaultAgenticScope agenticScope);

    protected abstract InvocationHandler createSubAgentWithAgenticScope(DefaultAgenticScope agenticScope);
}
