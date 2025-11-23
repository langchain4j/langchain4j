package dev.langchain4j.agentic.internal;

import static dev.langchain4j.agentic.internal.AgentUtil.agenticSystemDataTypes;
import static dev.langchain4j.agentic.internal.AgentUtil.argumentsFromMethod;
import static dev.langchain4j.agentic.internal.AgentUtil.hasStreamingAgent;
import static dev.langchain4j.agentic.internal.AgentUtil.uniqueAgentName;

import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.agent.AgentRequest;
import dev.langchain4j.agentic.agent.AgentResponse;
import dev.langchain4j.agentic.agent.ErrorContext;
import dev.langchain4j.agentic.agent.ErrorRecoveryResult;
import dev.langchain4j.agentic.planner.Action;
import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.planner.ChatMemoryAccessProvider;
import dev.langchain4j.agentic.planner.DefaultAgentInstance;
import dev.langchain4j.agentic.planner.InitPlanningContext;
import dev.langchain4j.agentic.planner.Planner;
import dev.langchain4j.agentic.planner.PlanningContext;
import dev.langchain4j.agentic.scope.AgentInvocation;
import dev.langchain4j.agentic.scope.AgentInvocationListener;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.agentic.scope.AgenticScopeAccess;
import dev.langchain4j.agentic.scope.AgenticScopeRegistry;
import dev.langchain4j.agentic.scope.DefaultAgenticScope;
import dev.langchain4j.agentic.scope.ResultWithAgenticScope;
import dev.langchain4j.internal.DefaultExecutorProvider;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.memory.ChatMemoryAccess;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class PlannerBasedInvocationHandler implements InvocationHandler {
    private final Executor executor;

    private final AgentInstance plannerAgent;
    private final Function<AgenticScope, Object> output;

    private final Consumer<AgentRequest> beforeListener;
    private final Consumer<AgentResponse> afterListener;

    private final Consumer<AgenticScope> beforeCall;

    private final DefaultAgenticScope agenticScope;

    private final Function<ErrorContext, ErrorRecoveryResult> errorHandler;

    private final AtomicReference<AgenticScopeRegistry> agenticScopeRegistry = new AtomicReference<>();

    private final AbstractServiceBuilder<?, ?> service;

    private final Supplier<Planner> plannerSupplier;

    public PlannerBasedInvocationHandler(AbstractServiceBuilder<?, ?> service, Supplier<Planner> plannerSupplier) {
        this(service, plannerSupplier, null);
    }

    private PlannerBasedInvocationHandler(
            AbstractServiceBuilder<?, ?> service, Supplier<Planner> plannerSupplier, DefaultAgenticScope agenticScope) {
        this.service = service;
        this.output = service.output;
        this.executor = service.executor;
        this.beforeCall = service.beforeCall;
        this.errorHandler = service.errorHandler;
        this.beforeListener = service.beforeListener;
        this.afterListener = service.afterListener;
        this.plannerSupplier = plannerSupplier;
        this.agenticScope = agenticScope;

        this.plannerAgent = new DefaultAgentInstance(
                service.agentServiceClass,
                service.name,
                uniqueAgentName(service.agentServiceClass, service.name),
                service.description,
                service.agentReturnType(),
                service.outputKey,
                service.agenticMethod != null ? argumentsFromMethod(service.agenticMethod) : List.of(),
                service.agentExecutors().stream().map(AgentInstance.class::cast).toList());
        agenticSystemDataTypes(this.plannerAgent);

        checkSubAgents();
    }

    private void checkSubAgents() {
        if (hasStreamingAgent(plannerAgent.subagents()) && !(plannerSupplier instanceof StreamingSubAgentsChecker)) {
            throw new IllegalArgumentException("Agent cannot be used as a sub-agent because it returns TokenStream.");
        }
        if (plannerSupplier instanceof StreamingSubAgentsChecker) {
            ((StreamingSubAgentsChecker) plannerSupplier).checkSubAgents(plannerAgent.subagents(), plannerAgent);
        }
    }

    public AgenticScopeOwner withAgenticScope(DefaultAgenticScope agenticScope) {
        return (AgenticScopeOwner) Proxy.newProxyInstance(
                plannerAgent.type().getClassLoader(),
                new Class<?>[] {plannerAgent.type(), AgentSpecification.class, AgenticScopeOwner.class},
                new PlannerBasedInvocationHandler(service, plannerSupplier, agenticScope));
    }

    public AgentInstance plannerInstance() {
        return plannerAgent;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) {
        AgenticScopeRegistry registry = agenticScopeRegistry();
        if (method.getDeclaringClass() == AgenticScopeOwner.class) {
            return switch (method.getName()) {
                case "withAgenticScope" -> withAgenticScope((DefaultAgenticScope) args[0]);
                case "registry" -> registry;
                default ->
                    throw new UnsupportedOperationException(
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

        if (method.getDeclaringClass() == AgentInstance.class) {
            return switch (method.getName()) {
                case "type" -> plannerAgent.type();
                case "name" -> plannerAgent.name();
                case "agentId" -> plannerAgent.agentId();
                case "description" -> plannerAgent.description();
                case "outputType" -> plannerAgent.outputType();
                case "outputKey" -> plannerAgent.outputKey();
                case "arguments" -> plannerAgent.arguments();
                case "subagents" -> plannerAgent.subagents();
                default ->
                    throw new UnsupportedOperationException(
                            "Unknown method on AgentInstance class : " + method.getName());
            };
        }

        if (method.getDeclaringClass() == AgentSpecification.class) {
            return switch (method.getName()) {
                case "async" -> false;
                case "beforeInvocation" -> {
                    beforeListener.accept((AgentRequest) args[0]);
                    yield null;
                }
                case "afterInvocation" -> {
                    afterListener.accept((AgentResponse) args[0]);
                    yield null;
                }
                default ->
                    throw new UnsupportedOperationException(
                            "Unknown method on AgentSpecification class : " + method.getName());
            };
        }

        if (method.getDeclaringClass() == Object.class) {
            return switch (method.getName()) {
                case "toString" ->
                    service.serviceType() + "<" + plannerAgent.type().getSimpleName() + ">";
                case "hashCode" -> System.identityHashCode(this);
                default ->
                    throw new UnsupportedOperationException("Unknown method on Object class : " + method.getName());
            };
        }

        if (method.getDeclaringClass() == ChatMemoryAccess.class) {
            Object memoryId = args[0];
            return accessChatMemory(registry.getOrCreate(memoryId), method.getName(), memoryId);
        }

        return executeAgentMethod(registry, method, args);
    }

    private AgenticScopeRegistry agenticScopeRegistry() {
        if (isRootCall()) {
            agenticScopeRegistry.compareAndSet(
                    null, new AgenticScopeRegistry(plannerAgent.type().getName()));
        }
        return agenticScopeRegistry.get();
    }

    private Object executeAgentMethod(AgenticScopeRegistry registry, Method method, Object[] args) {
        DefaultAgenticScope currentScope = currentAgenticScope(registry, method, args);
        writeAgenticScope(currentScope, method, args);
        beforeCall.accept(currentScope);

        if (isRootCall()) {
            currentScope.rootCallStarted(registry);
        }

        Planner planner = plannerSupplier.get();
        planner.init(new InitPlanningContext(currentScope, plannerAgent, plannerAgent.subagents()));
        Object result = new PlannerLoop(planner, currentScope).loop();

        if (isRootCall()) {
            currentScope.rootCallEnded(registry);
        }

        Object output = plannerAgent.outputKey() != null ? currentScope.readState(plannerAgent.outputKey()) : result;
        return method.getReturnType().equals(ResultWithAgenticScope.class)
                ? new ResultWithAgenticScope<>(currentScope, output)
                : output;
    }

    private class PlannerLoop implements AgentInvocationListener {
        private final Planner planner;
        private final DefaultAgenticScope agenticScope;

        private Action nextAction = null;

        private PlannerLoop(Planner planner, DefaultAgenticScope agenticScope) {
            this.planner = planner;
            this.agenticScope = agenticScope;
        }

        public Object loop() {
            nextAction = planner.firstAction(new PlanningContext(agenticScope, null));
            while (nextAction == null || !nextAction.isDone()) {
                if (nextAction == null) {
                    Thread.yield();
                    continue;
                }
                List<AgentExecutor> agents = ((Action.AgentCallAction) nextAction).agentsToCall();
                nextAction = null;
                switch (agents.size()) {
                    case 0 -> Thread.yield();
                    case 1 -> agents.get(0).execute(agenticScope, this);
                    default -> parallelExecution(agents);
                }
            }
            return result();
        }

        private void parallelExecution(List<AgentExecutor> agents) {
            Executor exec = executor != null ? executor : DefaultExecutorProvider.getDefaultExecutorService();
            var tasks = agents.stream()
                    .map(agentExecutor ->
                            CompletableFuture.supplyAsync(() -> agentExecutor.execute(agenticScope, this), exec))
                    .toList();
            try {
                for (Future<?> future : tasks) {
                    future.get();
                }
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
        }

        private Object result() {
            Object result = output != null ? output.apply(agenticScope) : nextAction.result();
            if (plannerAgent.outputKey() != null) {
                if (result != null) {
                    agenticScope.writeState(plannerAgent.outputKey(), result);
                    return result;
                } else {
                    return agenticScope.readState(plannerAgent.outputKey());
                }
            }
            return result;
        }

        @Override
        public void onAgentInvoked(AgentInvocation agentInvocation) {
            this.nextAction = composeActions(
                    this.nextAction, planner.nextAction(new PlanningContext(agenticScope, agentInvocation)));
        }

        private static Action composeActions(Action first, Action second) {
            if (first == null || first.isDone()) {
                return second;
            }
            if (second == null || second.isDone()) {
                return first;
            }

            List<AgentExecutor> agentsToCall = new ArrayList<>();
            agentsToCall.addAll(((Action.AgentCallAction) first).agentsToCall());
            agentsToCall.addAll(((Action.AgentCallAction) second).agentsToCall());
            return new Action.AgentCallAction(agentsToCall);
        }
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
                        .ifPresent(argName -> agenticScope.writeState(argName, args[index]));
            }
        }
    }

    private DefaultAgenticScope currentAgenticScope(AgenticScopeRegistry registry, Method method, Object[] args) {
        if (agenticScope != null) {
            return agenticScope;
        }

        Object memoryId = memoryId(method, args);
        DefaultAgenticScope newAgenticScope =
                memoryId != null ? registry.getOrCreate(memoryId) : registry.createEphemeralAgenticScope();
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

    private Object accessChatMemory(AgenticScope agenticScope, String methodName, Object memoryId) {
        ChatMemoryAccess chatMemoryAccess =
                ((ChatMemoryAccessProvider) plannerSupplier.get()).chatMemoryAccess(agenticScope);
        return switch (methodName) {
            case "getChatMemory" -> chatMemoryAccess.getChatMemory(memoryId);
            case "evictChatMemory" -> chatMemoryAccess.evictChatMemory(memoryId);
            default ->
                throw new UnsupportedOperationException("Unknown method on ChatMemoryAccess class : " + methodName);
        };
    }
}
