package dev.langchain4j.agentic;

import static dev.langchain4j.agentic.declarative.DeclarativeUtil.checkReturnType;
import static dev.langchain4j.agentic.declarative.DeclarativeUtil.configureAgent;
import static dev.langchain4j.agentic.declarative.DeclarativeUtil.invokeStatic;
import static dev.langchain4j.agentic.internal.AgentInvoker.parameterName;
import static dev.langchain4j.agentic.internal.AgentUtil.AGENTIC_SCOPE_ARG_NAME;
import static dev.langchain4j.agentic.internal.AgentUtil.LOOP_COUNTER_ARG_NAME;
import static dev.langchain4j.agentic.internal.AgentUtil.agentInvocationArguments;
import static dev.langchain4j.agentic.internal.AgentUtil.agentToExecutor;
import static dev.langchain4j.agentic.internal.AgentUtil.argumentsFromMethod;
import static dev.langchain4j.agentic.internal.AgentUtil.getAnnotatedMethodOnClass;
import static dev.langchain4j.agentic.internal.AgentUtil.validateAgentClass;
import static dev.langchain4j.internal.Utils.isNullOrBlank;

import dev.langchain4j.agentic.agent.AgentBuilder;
import dev.langchain4j.agentic.agent.UntypedAgentBuilder;
import dev.langchain4j.agentic.declarative.AgentListenerSupplier;
import dev.langchain4j.agentic.internal.InternalAgent;
import dev.langchain4j.agentic.observability.AgentListener;
import dev.langchain4j.agentic.agent.ErrorContext;
import dev.langchain4j.agentic.agent.ErrorRecoveryResult;
import dev.langchain4j.agentic.declarative.A2AClientAgent;
import dev.langchain4j.agentic.declarative.ActivationCondition;
import dev.langchain4j.agentic.declarative.ChatMemoryProviderSupplier;
import dev.langchain4j.agentic.declarative.ChatModelSupplier;
import dev.langchain4j.agentic.declarative.PlannerAgent;
import dev.langchain4j.agentic.declarative.PlannerSupplier;
import dev.langchain4j.agentic.internal.AgentUtil;
import dev.langchain4j.agentic.planner.AgentArgument;
import dev.langchain4j.agentic.planner.AgenticService;
import dev.langchain4j.agentic.planner.Planner;
import dev.langchain4j.agentic.planner.PlannerBasedService;
import dev.langchain4j.agentic.planner.PlannerBasedServiceImpl;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.agentic.declarative.ConditionalAgent;
import dev.langchain4j.agentic.declarative.ErrorHandler;
import dev.langchain4j.agentic.declarative.ExitCondition;
import dev.langchain4j.agentic.declarative.HumanInTheLoopResponseSupplier;
import dev.langchain4j.agentic.declarative.LoopAgent;
import dev.langchain4j.agentic.declarative.Output;
import dev.langchain4j.agentic.declarative.ParallelAgent;
import dev.langchain4j.agentic.declarative.ParallelExecutor;
import dev.langchain4j.agentic.declarative.SequenceAgent;
import dev.langchain4j.agentic.declarative.SupervisorRequest;
import dev.langchain4j.agentic.internal.A2AClientBuilder;
import dev.langchain4j.agentic.internal.A2AService;
import dev.langchain4j.agentic.internal.AgentExecutor;
import dev.langchain4j.agentic.internal.AgentInvoker;
import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.supervisor.SupervisorAgent;
import dev.langchain4j.agentic.supervisor.SupervisorAgentService;
import dev.langchain4j.agentic.supervisor.SupervisorAgentServiceImpl;
import dev.langchain4j.agentic.workflow.ConditionalAgentService;
import dev.langchain4j.agentic.workflow.HumanInTheLoop;
import dev.langchain4j.agentic.workflow.LoopAgentService;
import dev.langchain4j.agentic.workflow.ParallelAgentService;
import dev.langchain4j.agentic.workflow.SequentialAgentService;
import dev.langchain4j.agentic.workflow.WorkflowAgentsBuilder;
import dev.langchain4j.agentic.workflow.impl.ConditionalAgentServiceImpl;
import dev.langchain4j.agentic.workflow.impl.LoopAgentServiceImpl;
import dev.langchain4j.agentic.workflow.impl.ParallelAgentServiceImpl;
import dev.langchain4j.agentic.workflow.impl.SequentialAgentServiceImpl;
import dev.langchain4j.agentic.workflow.impl.WorkflowAgentsBuilderImpl;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.model.chat.ChatModel;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.concurrent.Executor;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * Provides static factory methods to create and configure various types of agent services.
 */
public class AgenticServices {

    private AgenticServices() {}

    private enum WorkflowBuilderProvider {
        INSTANCE;

        private WorkflowAgentsBuilder workflowAgentsBuilder;

        WorkflowBuilderProvider() {
            internalSetWorkflowAgentsBuilder(loadWorkflowAgentsBuilder());
        }

        private static WorkflowAgentsBuilder loadWorkflowAgentsBuilder() {
            ServiceLoader<WorkflowAgentsBuilder> loader = ServiceLoader.load(WorkflowAgentsBuilder.class);

            for (WorkflowAgentsBuilder builder : loader) {
                return builder; // Return the first builder found
            }
            return WorkflowAgentsBuilderImpl.INSTANCE; // Use default implementation
        }

        private void internalSetWorkflowAgentsBuilder(final WorkflowAgentsBuilder workflowAgentsBuilder) {
            this.workflowAgentsBuilder = workflowAgentsBuilder;
        }
    }

    /**
     * Explicitly set a WorkflowAgentsBuilder.
     */
    public static void setWorkflowAgentsBuilder(WorkflowAgentsBuilder workflowAgentsBuilder) {
        WorkflowBuilderProvider.INSTANCE.internalSetWorkflowAgentsBuilder(workflowAgentsBuilder);
    }

    private static WorkflowAgentsBuilder workflowAgentsBuilder() {
        return WorkflowBuilderProvider.INSTANCE.workflowAgentsBuilder;
    }

    /**
     * Creates an agent builder for untyped agents.
     *
     * @return a new UntypedAgentBuilder instance
     */
    public static UntypedAgentBuilder agentBuilder() {
        return new UntypedAgentBuilder();
    }

    /**
     * Creates an agent builder for the given agent service class.
     *
     * @param agentServiceClass the class of the agent service
     * @return a new AgentBuilder instance
     */
    public static <T> AgentBuilder<T, AgentBuilder<T, ?>> agentBuilder(Class<T> agentServiceClass) {
        return new AgentBuilder<>(agentServiceClass);
    }

    /**
     * Creates a builder for an agent implementing the human-in-the-loop pattern.
     */
    public static HumanInTheLoop.HumanInTheLoopBuilder humanInTheLoopBuilder() {
        return new HumanInTheLoop.HumanInTheLoopBuilder();
    }

    /**
     * Creates a builder for an untyped agent implementing a workflow sequence of its subagents.
     */
    public static SequentialAgentService<UntypedAgent> sequenceBuilder() {
        return workflowAgentsBuilder().sequenceBuilder();
    }

    /**
     * Creates a builder for an agent implementing a workflow sequence of its subagents
     * that can be invoked in a strongly typed way through the provided agent service interface.
     *
     * @param agentServiceClass the class of the agent service
     */
    public static <T> SequentialAgentService<T> sequenceBuilder(Class<T> agentServiceClass) {
        return workflowAgentsBuilder().sequenceBuilder(agentServiceClass);
    }

    /**
     * Creates a builder for an untyped agent implementing a parallel workflow of its subagents.
     */
    public static ParallelAgentService<UntypedAgent> parallelBuilder() {
        return workflowAgentsBuilder().parallelBuilder();
    }

    /**
     * Creates a builder for an agent implementing a parallel workflow of its subagents
     * that can be invoked in a strongly typed way through the provided agent service interface.
     *
     * @param agentServiceClass the class of the agent service
     */
    public static <T> ParallelAgentService<T> parallelBuilder(Class<T> agentServiceClass) {
        return workflowAgentsBuilder().parallelBuilder(agentServiceClass);
    }

    /**
     * Creates a builder for an untyped agent implementing a loop workflow of its subagents.
     */
    public static LoopAgentService<UntypedAgent> loopBuilder() {
        return workflowAgentsBuilder().loopBuilder();
    }

    /**
     * Creates a builder for an agent implementing a loop workflow of its subagents
     * that can be invoked in a strongly typed way through the provided agent service interface.
     *
     * @param agentServiceClass the class of the agent service
     */
    public static <T> LoopAgentService<T> loopBuilder(Class<T> agentServiceClass) {
        return workflowAgentsBuilder().loopBuilder(agentServiceClass);
    }

    /**
     * Creates a builder for an untyped agent implementing a conditional workflow of its subagents.
     */
    public static ConditionalAgentService<UntypedAgent> conditionalBuilder() {
        return workflowAgentsBuilder().conditionalBuilder();
    }

    /**
     * Creates a builder for an agent implementing a conditional workflow of its subagents
     * that can be invoked in a strongly typed way through the provided agent service interface.
     *
     * @param agentServiceClass the class of the agent service
     */
    public static <T> ConditionalAgentService<T> conditionalBuilder(Class<T> agentServiceClass) {
        return workflowAgentsBuilder().conditionalBuilder(agentServiceClass);
    }

    /**
     * Creates a builder for a supervisor agent service that can be used to manage and supervise other agents.
     * This is useful for building complex agentic systems where one agent oversees the execution of others.
     */
    public static SupervisorAgentService<SupervisorAgent> supervisorBuilder() {
        return SupervisorAgentServiceImpl.builder();
    }

    /**
     * Creates a builder for a supervisor agent service that can be used to manage and supervise other agents.
     * This is useful for building complex agentic systems where one agent oversees the execution of others.
     *
     * @param agentServiceClass the class of the agent service
     */
    public static <T> SupervisorAgentService<T> supervisorBuilder(Class<T> agentServiceClass) {
        return SupervisorAgentServiceImpl.builder(agentServiceClass);
    }

    /**
     * Creates a builder for a customizable planner agent service.
     */
    public static PlannerBasedService<UntypedAgent> plannerBuilder() {
        return PlannerBasedServiceImpl.builder(UntypedAgent.class);
    }

    /**
     * Creates a builder for a customizable planner agent service.
     *
     * @param agentServiceClass the class of the agent service
     */
    public static <T> PlannerBasedService<T> plannerBuilder(Class<T> agentServiceClass) {
        return PlannerBasedServiceImpl.builder(agentServiceClass);
    }

    /**
     * Creates a builder for an A2A client that can be used to interact with agents over the A2A protocol.
     * This is useful for building agentic systems that communicate with remote agents.
     *
     * @param a2aServerUrl the URL of the A2A server
     * @return a new A2AClientBuilder instance
     */
    public static A2AClientBuilder<UntypedAgent> a2aBuilder(String a2aServerUrl) {
        return a2aBuilder(a2aServerUrl, UntypedAgent.class);
    }

    /**
     * Creates a builder for an A2A client that can be used to interact with agents over the A2A protocol.
     * This is useful for building agentic systems that communicate with remote agents.
     *
     * @param a2aServerUrl the URL of the A2A server
     * @param agentServiceClass the class of the agent service
     * @return a new A2AClientBuilder instance
     */
    public static <T> A2AClientBuilder<T> a2aBuilder(String a2aServerUrl, Class<T> agentServiceClass) {
        return A2AService.get().a2aBuilder(a2aServerUrl, agentServiceClass);
    }

    public interface DeclarativeAgentCreationContext<T> {
        Class<T> agentServiceClass();

        AgentBuilder<T, ?> agentBuilder();
    }

    public record DefaultDeclarativeAgentCreationContext<T>(Class<T> agentServiceClass, AgentBuilder<T, ?> agentBuilder)
            implements DeclarativeAgentCreationContext<T> {}

    /**
     * Creates an instance of an agentic system defined through the declarative API.
     *
     * @param agentServiceClass the class of the agent service
     */
    public static <T> T createAgenticSystem(Class<T> agentServiceClass) {
        return createAgenticSystem(agentServiceClass, declarativeChatModel(agentServiceClass));
    }

    /**
     * Creates an instance of an agentic system defined through the declarative API and using the provided ChatModel.
     *
     * @param agentServiceClass the class of the agent service
     * @param chatModel the ChatModel used by default for all agents participating in this agentic system
     */
    public static <T> T createAgenticSystem(Class<T> agentServiceClass, ChatModel chatModel) {
        return createAgenticSystem(agentServiceClass, chatModel, ctx -> {});
    }

    /**
     * Creates an instance of an agentic system defined through the declarative API.
     *
     * @param agentServiceClass the class of the agent service
     * @param agentConfigurator A callback to tweak the configuration of each agent created in this agentic system
     */
    public static <T> T createAgenticSystem(
            Class<T> agentServiceClass, Consumer<DeclarativeAgentCreationContext> agentConfigurator) {
        return createAgenticSystem(agentServiceClass, declarativeChatModel(agentServiceClass), agentConfigurator);
    }

    /**
     * Creates an instance of an agentic system defined through the declarative API and using the provided ChatModel.
     *
     * @param agentServiceClass the class of the agent service
     * @param chatModel the ChatModel used by default for all agents participating in this agentic system
     * @param agentConfigurator A callback to tweak the configuration of each agent created in this agentic system
     */
    public static <T> T createAgenticSystem(
            Class<T> agentServiceClass,
            ChatModel chatModel,
            Consumer<DeclarativeAgentCreationContext> agentConfigurator) {
        T agent = createComposedAgent(agentServiceClass, chatModel, agentConfigurator);

        if (agent == null) {
            var agentBuilder = agentBuilder(agentServiceClass);
            configureAgent(agentServiceClass, chatModel, agentBuilder, agentConfigurator);
            agent = agentBuilder.build();
        }

        if (agent == null) {
            throw new IllegalArgumentException("Provided class " + agentServiceClass.getName() + " is not an agent.");
        }

        return agent;
    }

    private static <T> ChatModel declarativeChatModel(Class<T> agentServiceClass) {
        return selectMethod(
                        agentServiceClass,
                        method -> method.isAnnotationPresent(ChatModelSupplier.class)
                                && method.getReturnType() == ChatModel.class
                                && method.getParameterCount() == 0)
                .map(method -> (ChatModel) invokeStatic(method))
                .orElse(null);
    }

    private static <T> T createComposedAgent(
            Class<T> agentServiceClass,
            ChatModel chatModel,
            Consumer<DeclarativeAgentCreationContext> agentConfigurator) {
        Optional<Method> sequenceMethod = getAnnotatedMethodOnClass(agentServiceClass, SequenceAgent.class);
        if (sequenceMethod.isPresent()) {
            return buildSequentialAgent(agentServiceClass, sequenceMethod.get(), chatModel, agentConfigurator);
        }

        Optional<Method> loopMethod = getAnnotatedMethodOnClass(agentServiceClass, LoopAgent.class);
        if (loopMethod.isPresent()) {
            return buildLoopAgent(agentServiceClass, loopMethod.get(), chatModel, agentConfigurator);
        }

        Optional<Method> conditionalMethod = getAnnotatedMethodOnClass(agentServiceClass, ConditionalAgent.class);
        if (conditionalMethod.isPresent()) {
            return buildConditionalAgent(agentServiceClass, conditionalMethod.get(), chatModel, agentConfigurator);
        }

        Optional<Method> parallelMethod = getAnnotatedMethodOnClass(agentServiceClass, ParallelAgent.class);
        if (parallelMethod.isPresent()) {
            return buildParallelAgent(agentServiceClass, parallelMethod.get(), chatModel, agentConfigurator);
        }

        Optional<Method> supervisorMethod =
                getAnnotatedMethodOnClass(agentServiceClass, dev.langchain4j.agentic.declarative.SupervisorAgent.class);
        if (supervisorMethod.isPresent()) {
            return buildSupervisorAgent(agentServiceClass, supervisorMethod.get(), chatModel, agentConfigurator);
        }

        Optional<Method> plannerMethod = getAnnotatedMethodOnClass(agentServiceClass, PlannerAgent.class);
        if (plannerMethod.isPresent()) {
            return buildPlannerAgent(agentServiceClass, plannerMethod.get(), chatModel, agentConfigurator);
        }

        return null;
    }

    private static <T> T buildSequentialAgent(
            Class<T> agentServiceClass,
            Method agentMethod,
            ChatModel chatModel,
            Consumer<DeclarativeAgentCreationContext> agentConfigurator) {
        SequenceAgent sequenceAgent = agentMethod.getAnnotation(SequenceAgent.class);
        var builder = sequenceBuilder(agentServiceClass)
                .subAgents(createSubagents(sequenceAgent.subAgents(), chatModel, agentConfigurator));

        buildAgentSpecs(
                agentServiceClass,
                agentMethod,
                sequenceAgent.name(),
                sequenceAgent.description(),
                AgentUtil.outputKey(sequenceAgent.outputKey(), sequenceAgent.typedOutputKey()),
                builder);

        return builder.build();
    }

    private static <T> T buildLoopAgent(
            Class<T> agentServiceClass,
            Method agentMethod,
            ChatModel chatModel,
            Consumer<DeclarativeAgentCreationContext> agentConfigurator) {
        LoopAgent loopAgent = agentMethod.getAnnotation(LoopAgent.class);
        var builder = loopBuilder(agentServiceClass)
                .subAgents(createSubagents(loopAgent.subAgents(), chatModel, agentConfigurator))
                .maxIterations(loopAgent.maxIterations());

        buildAgentSpecs(
                agentServiceClass,
                agentMethod,
                loopAgent.name(),
                loopAgent.description(),
                AgentUtil.outputKey(loopAgent.outputKey(), loopAgent.typedOutputKey()),
                builder);

        predicateMethod(agentServiceClass, method -> method.isAnnotationPresent(ExitCondition.class))
                .map(method -> {
                    builder.testExitAtLoopEnd(
                            method.getAnnotation(ExitCondition.class).testExitAtLoopEnd());
                    return method;
                })
                .ifPresent(method -> builder.exitCondition(
                        method.getAnnotation(ExitCondition.class).description(), loopExitConditionPredicate(method)));

        return builder.build();
    }

    private static <T> T buildConditionalAgent(
            Class<T> agentServiceClass,
            Method agentMethod,
            ChatModel chatModel,
            Consumer<DeclarativeAgentCreationContext> agentConfigurator) {
        ConditionalAgent conditionalAgent = agentMethod.getAnnotation(ConditionalAgent.class);
        var builder = conditionalBuilder(agentServiceClass);

        buildAgentSpecs(
                agentServiceClass,
                agentMethod,
                conditionalAgent.name(),
                conditionalAgent.description(),
                AgentUtil.outputKey(conditionalAgent.outputKey(), conditionalAgent.typedOutputKey()),
                builder);

        for (Class<?> subagent : conditionalAgent.subAgents()) {
            predicateMethod(agentServiceClass, method -> {
                        ActivationCondition activationCondition = method.getAnnotation(ActivationCondition.class);
                        return activationCondition != null
                                && Arrays.asList(activationCondition.value()).contains(subagent);
                    })
                    .ifPresent(method ->
                            builder.subAgent(method.getAnnotation(ActivationCondition.class).description(),
                                    agenticScopePredicate(method),
                                    createSubagent(subagent, chatModel, agentConfigurator)));
        }

        return builder.build();
    }

    private static <T> T buildParallelAgent(
            Class<T> agentServiceClass,
            Method agentMethod,
            ChatModel chatModel,
            Consumer<DeclarativeAgentCreationContext> agentConfigurator) {
        ParallelAgent parallelAgent = agentMethod.getAnnotation(ParallelAgent.class);
        var builder = parallelBuilder(agentServiceClass)
                .subAgents(createSubagents(parallelAgent.subAgents(), chatModel, agentConfigurator));

        buildAgentSpecs(
                agentServiceClass,
                agentMethod,
                parallelAgent.name(),
                parallelAgent.description(),
                AgentUtil.outputKey(parallelAgent.outputKey(), parallelAgent.typedOutputKey()),
                builder);

        selectMethod(
                        agentServiceClass,
                        method -> method.isAnnotationPresent(ParallelExecutor.class)
                                && Executor.class.isAssignableFrom(method.getReturnType())
                                && method.getParameterCount() == 0)
                .map(method -> {
                    try {
                        return (Executor) method.invoke(null);
                    } catch (Exception e) {
                        throw new RuntimeException("Error invoking executor method: " + method.getName(), e);
                    }
                })
                .ifPresent(builder::executor);

        return builder.build();
    }

    private static <T> T buildPlannerAgent(
            Class<T> agentServiceClass,
            Method agentMethod,
            ChatModel chatModel,
            Consumer<DeclarativeAgentCreationContext> agentConfigurator) {
        PlannerAgent plannerAgent = agentMethod.getAnnotation(PlannerAgent.class);
        var builder = new PlannerBasedServiceImpl<>(agentServiceClass, agentMethod)
                .subAgents(createSubagents(plannerAgent.subAgents(), chatModel, agentConfigurator));

        buildAgentSpecs(
                agentServiceClass,
                agentMethod,
                plannerAgent.name(),
                plannerAgent.description(),
                AgentUtil.outputKey(plannerAgent.outputKey(), plannerAgent.typedOutputKey()),
                builder);

        getAnnotatedMethodOnClass(agentServiceClass, PlannerSupplier.class)
                .ifPresentOrElse(method -> {
                    checkReturnType(method, Planner.class);
                    builder.planner(() -> invokeStatic(method));
                }, () -> new IllegalArgumentException(
                    "A planner agent requires a method annotated with @PlannerSupplier that returns the Planner instance."));

        return builder.build();
    }

    private static <T> void buildAgentSpecs(
            Class<T> agentServiceClass,
            Method agentMethod,
            String name,
            String description,
            String outputKey,
            AgenticService<?, ?> builder) {
        if (!isNullOrBlank(name)) {
            builder.name(name);
        } else {
            builder.name(agentMethod.getName());
        }
        if (!isNullOrBlank(description)) {
            builder.description(description);
        }
        if (!isNullOrBlank(outputKey)) {
            builder.outputKey(outputKey);
        }

        selectMethod(agentServiceClass, method -> method.isAnnotationPresent(Output.class))
                .map(m -> AgenticServices.agenticScopeFunction(m, Object.class))
                .ifPresent(builder::output);

        buildAgentFeatures(agentServiceClass, builder);
    }

    private static <T> T buildSupervisorAgent(
            Class<T> agentServiceClass,
            Method agentMethod,
            ChatModel chatModel,
            Consumer<DeclarativeAgentCreationContext> agentConfigurator) {
        dev.langchain4j.agentic.declarative.SupervisorAgent supervisorAgent =
                agentMethod.getAnnotation(dev.langchain4j.agentic.declarative.SupervisorAgent.class);
        var builder = new SupervisorAgentServiceImpl<>(agentServiceClass, agentMethod)
                .maxAgentsInvocations(supervisorAgent.maxAgentsInvocations())
                .contextGenerationStrategy(supervisorAgent.contextStrategy())
                .responseStrategy(supervisorAgent.responseStrategy())
                .subAgents(createSubagents(supervisorAgent.subAgents(), chatModel, agentConfigurator));

        if (!isNullOrBlank(supervisorAgent.name())) {
            builder.name(supervisorAgent.name());
        } else {
            builder.name(agentMethod.getName());
        }
        if (!isNullOrBlank(supervisorAgent.description())) {
            builder.description(supervisorAgent.description());
        }
        builder.outputKey(AgentUtil.outputKey(supervisorAgent.outputKey(), supervisorAgent.typedOutputKey()));

        selectMethod(
                        agentServiceClass,
                        method -> method.isAnnotationPresent(SupervisorRequest.class)
                                && method.getReturnType() == String.class)
                .map(m -> AgenticServices.agenticScopeFunction(m, String.class))
                .ifPresent(builder::requestGenerator);

        selectMethod(
                        agentServiceClass,
                        method -> method.isAnnotationPresent(ChatModelSupplier.class)
                                && method.getReturnType() == ChatModel.class
                                && method.getParameterCount() == 0)
                .map(method -> (ChatModel) invokeStatic(method))
                .ifPresentOrElse(builder::chatModel, () -> builder.chatModel(chatModel));

        selectMethod(
                        agentServiceClass,
                        method -> method.isAnnotationPresent(ChatMemoryProviderSupplier.class)
                                && method.getReturnType() == ChatMemory.class
                                && method.getParameterCount() == 1)
                .map(method -> (ChatMemoryProvider) memoryId -> invokeStatic(method, memoryId))
                .ifPresent(builder::chatMemoryProvider);

        selectMethod(agentServiceClass, method -> method.isAnnotationPresent(Output.class))
                .map(m -> AgenticServices.agenticScopeFunction(m, Object.class))
                .ifPresent(builder::output);

        buildAgentFeatures(agentServiceClass, builder);

        return builder.build();
    }

    private static void buildAgentFeatures(Class<?> agentServiceClass, AgenticService<?, ?> builder) {
        buildErrorHandler(agentServiceClass).ifPresent(builder::errorHandler);
        buildListener(agentServiceClass, builder);
    }

    private static <T> Optional<Function<ErrorContext, ErrorRecoveryResult>> buildErrorHandler(
            Class<T> agentServiceClass) {
        return selectMethod(agentServiceClass, method -> method.isAnnotationPresent(ErrorHandler.class))
                .map(m -> errorContext -> invokeStatic(m, errorContext));
    }

    private static void buildListener(Class<?> agentServiceClass, AgenticService<?, ?> builder) {
        getAnnotatedMethodOnClass(agentServiceClass, AgentListenerSupplier.class)
                .ifPresent(listenerMethod -> {
                    checkReturnType(listenerMethod, AgentListener.class);
                    builder.listener(invokeStatic(listenerMethod));
                });
    }

    private static Optional<Method> predicateMethod(Class<?> agentServiceClass, Predicate<Method> methodSelector) {
        return selectMethod(
                agentServiceClass,
                methodSelector.and(m -> (m.getReturnType() == boolean.class || m.getReturnType() == Boolean.class)));
    }

    private static Optional<Method> selectMethod(Class<?> agentServiceClass, Predicate<Method> methodSelector) {
        for (Method method : agentServiceClass.getMethods()) {
            if (methodSelector.test(method) && Modifier.isStatic(method.getModifiers())) {
                return Optional.of(method);
            }
        }
        if (agentServiceClass.getSuperclass() != null) {
            Optional<Method> method = selectMethod(agentServiceClass.getSuperclass(), methodSelector);
            if (method.isPresent()) {
                return method;
            }
        }
        for (Class<?> interf : agentServiceClass.getInterfaces()) {
            Optional<Method> method = selectMethod(interf, methodSelector);
            if (method.isPresent()) {
                return method;
            }
        }
        return Optional.empty();
    }

    private static BiPredicate<AgenticScope, Integer> loopExitConditionPredicate(Method predicateMethod) {
        List<AgentArgument> agentArguments = argumentsFromMethod(predicateMethod);
        return (agenticScope, loopCounter) -> {
            try {
                Object[] args = agentInvocationArguments(
                                agenticScope,
                                agentArguments,
                                Map.of(AGENTIC_SCOPE_ARG_NAME, agenticScope, LOOP_COUNTER_ARG_NAME, loopCounter))
                        .positionalArgs();
                return (boolean) predicateMethod.invoke(null, args);
            } catch (Exception e) {
                throw new RuntimeException("Error invoking exit predicate method: " + predicateMethod.getName(), e);
            }
        };
    }

    private static Predicate<AgenticScope> agenticScopePredicate(Method predicateMethod) {
        return agenticScope ->
                agenticScopeFunction(predicateMethod, boolean.class).apply(agenticScope);
    }

    private static <T> Function<AgenticScope, T> agenticScopeFunction(Method functionMethod, Class<T> targetClass) {
        List<AgentArgument> agentArguments = argumentsFromMethod(functionMethod);
        return agenticScope -> {
            try {
                Object[] args = agentInvocationArguments(
                                agenticScope, agentArguments, Map.of(AGENTIC_SCOPE_ARG_NAME, agenticScope))
                        .positionalArgs();
                return (T) functionMethod.invoke(null, args);
            } catch (Exception e) {
                throw new RuntimeException("Error invoking method: " + functionMethod.getName(), e);
            }
        };
    }

    private static List<AgentExecutor> createSubagents(
            Class<?>[] subAgents, ChatModel chatModel, Consumer<DeclarativeAgentCreationContext> agentConfigurator) {
        return Stream.of(subAgents)
                .map(subagent -> createSubagent(subagent, chatModel, agentConfigurator))
                .toList();
    }

    private static AgentExecutor createSubagent(
            Class<?> subgentClass, ChatModel chatModel, Consumer<DeclarativeAgentCreationContext> agentConfigurator) {
        AgentExecutor agentExecutor = createBuiltInAgentExecutor(subgentClass, chatModel, agentConfigurator);
        if (agentExecutor != null) {
            return agentExecutor;
        }

        AgentBuilder<?, ?> agentBuilder = agentBuilder(subgentClass);
        configureAgent(subgentClass, chatModel, agentBuilder, agentConfigurator);

        return agentToExecutor(agentBuilder.build());
    }

    private static AgentExecutor createBuiltInAgentExecutor(
            Class<?> agentServiceClass,
            ChatModel chatModel,
            Consumer<DeclarativeAgentCreationContext> agentConfigurator) {
        Optional<Method> sequenceMethod = getAnnotatedMethodOnClass(agentServiceClass, SequenceAgent.class);
        if (sequenceMethod.isPresent()) {
            Method method = sequenceMethod.get();
            InternalAgent agent =
                    (InternalAgent) buildSequentialAgent(agentServiceClass, method, chatModel, agentConfigurator);
            return new AgentExecutor(AgentInvoker.fromMethod(agent, method), agent);
        }

        Optional<Method> loopMethod = getAnnotatedMethodOnClass(agentServiceClass, LoopAgent.class);
        if (loopMethod.isPresent()) {
            Method method = loopMethod.get();
            InternalAgent agent =
                    (InternalAgent) buildLoopAgent(agentServiceClass, method, chatModel, agentConfigurator);
            return new AgentExecutor(AgentInvoker.fromMethod(agent, method), agent);
        }

        Optional<Method> conditionalMethod = getAnnotatedMethodOnClass(agentServiceClass, ConditionalAgent.class);
        if (conditionalMethod.isPresent()) {
            Method method = conditionalMethod.get();
            InternalAgent agent =
                    (InternalAgent) buildConditionalAgent(agentServiceClass, method, chatModel, agentConfigurator);
            return new AgentExecutor(AgentInvoker.fromMethod(agent, method), agent);
        }

        Optional<Method> parallelMethod = getAnnotatedMethodOnClass(agentServiceClass, ParallelAgent.class);
        if (parallelMethod.isPresent()) {
            Method method = parallelMethod.get();
            InternalAgent agent =
                    (InternalAgent) buildParallelAgent(agentServiceClass, method, chatModel, agentConfigurator);
            return new AgentExecutor(AgentInvoker.fromMethod(agent, method), agent);
        }

        Optional<Method> supervisorMethod =
                getAnnotatedMethodOnClass(agentServiceClass, dev.langchain4j.agentic.declarative.SupervisorAgent.class);
        if (supervisorMethod.isPresent()) {
            Method method = supervisorMethod.get();
            InternalAgent agent =
                    (InternalAgent) buildSupervisorAgent(agentServiceClass, method, chatModel, agentConfigurator);
            return new AgentExecutor(AgentInvoker.fromMethod(agent, method), agent);
        }

        Optional<Method> humanInTheLoopMethod =
                getAnnotatedMethodOnClass(agentServiceClass, dev.langchain4j.agentic.declarative.HumanInTheLoop.class);
        if (humanInTheLoopMethod.isPresent()) {
            return createHumanInTheLoopAgent(agentServiceClass, humanInTheLoopMethod.get());
        }

        Optional<Method> a2aClientMethod = getAnnotatedMethodOnClass(agentServiceClass, A2AClientAgent.class);
        if (a2aClientMethod.isPresent()) {
            return createA2AClientAgent(agentServiceClass, a2aClientMethod.get());
        }

        if (!agentServiceClass.isInterface()) {
            Method agenticMethod = nonAiAgentMethod(agentServiceClass);
            if (agenticMethod != null) {
                if (agenticMethod.getParameterCount() == 0) {
                    return agentToExecutor(new AgentAction(() -> invokeStatic(agenticMethod)));
                }
                if (agenticMethod.getParameterCount() == 1
                        && AgenticScope.class.isAssignableFrom(agenticMethod.getParameterTypes()[0])) {
                    return agentToExecutor(
                            new AgenticScopeAction((agenticScope -> invokeStatic(agenticMethod, agenticScope))));
                }
            }
        }

        return null;
    }

    private static AgentExecutor createA2AClientAgent(Class<?> agentServiceClass, Method a2aMethod) {
        var a2aClient = a2aMethod.getAnnotation(A2AClientAgent.class);
        var a2aClientBuilder = a2aBuilder(a2aClient.a2aServerUrl(), agentServiceClass)
                .inputKeys(Stream.of(a2aMethod.getParameters())
                        .map(AgentInvoker::parameterName)
                        .toArray(String[]::new))
                .outputKey(AgentUtil.outputKey(a2aClient.outputKey(), a2aClient.typedOutputKey()))
                .async(a2aClient.async());

        getAnnotatedMethodOnClass(agentServiceClass, AgentListenerSupplier.class)
                .ifPresent(method -> {
                    checkReturnType(method, AgentListener.class);
                    a2aClientBuilder.listener(invokeStatic(method));
                });

        return agentToExecutor(a2aClientBuilder.build());
    }

    private static AgentExecutor createHumanInTheLoopAgent(Class<?> agentServiceClass, Method method) {
        var humanInTheLoop = method.getAnnotation(dev.langchain4j.agentic.declarative.HumanInTheLoop.class);
        if (method.getParameterCount() != 1) {
            throw new IllegalArgumentException("Method " + method.getName() + " annotated with @"
                    + HumanInTheLoop.class.getSimpleName() + " must have exactly one parameter");
        }

        var humanInTheLoopBuilder = humanInTheLoopBuilder()
                .description(humanInTheLoop.description())
                .outputKey(humanInTheLoop.outputKey())
                .async(humanInTheLoop.async())
                .inputKey(parameterName(method.getParameters()[0]))
                .requestWriter(arg -> invokeStatic(method, arg));

        getAnnotatedMethodOnClass(agentServiceClass, HumanInTheLoopResponseSupplier.class)
                .ifPresentOrElse(
                        readerMethod -> humanInTheLoopBuilder.responseReader(() -> invokeStatic(readerMethod)), () -> {
                            throw new IllegalArgumentException("Human in the loop class " + agentServiceClass.getName()
                                    + " must have a static method annotated with @"
                                    + HumanInTheLoopResponseSupplier.class.getSimpleName());
                        });

        getAnnotatedMethodOnClass(agentServiceClass, AgentListenerSupplier.class)
                .ifPresent(listenerMethod -> {
                    checkReturnType(listenerMethod, AgentListener.class);
                    humanInTheLoopBuilder.listener(invokeStatic(listenerMethod));
                });

        return agentToExecutor(humanInTheLoopBuilder.build());
    }

    private static Method nonAiAgentMethod(Class<?> agentServiceClass) {
        Method agenticMethod = null;
        for (Method method : agentServiceClass.getDeclaredMethods()) {
            if (method.isAnnotationPresent(Agent.class) && Modifier.isStatic(method.getModifiers())) {
                if (agenticMethod != null) {
                    throw new IllegalArgumentException(
                            "Multiple agent methods found in class: " + agentServiceClass.getName());
                }
                agenticMethod = method;
            }
        }
        return agenticMethod;
    }

    /**
     * Wraps a runnable into an agent action that can be executed within the context of an agent.
     *
     * @param runnable the runnable to be executed
     * @return an AgentAction that encapsulates the runnable
     */
    public static AgentAction agentAction(AgentAction.NonThrowingRunnable runnable) {
        return new AgentAction(runnable);
    }

    public static class AgentAction {
        private final NonThrowingRunnable runnable;

        @FunctionalInterface
        public interface NonThrowingRunnable {
            void run() throws Exception;
        }

        private AgentAction(NonThrowingRunnable runnable) {
            this.runnable = runnable;
        }

        @Agent
        public void run() {
            try {
                runnable.run();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * Wraps a consumer of the AgenticScope into an agent action that can be executed within the context of an agent.
     *
     * @param consumer the consumer to be executed
     * @return an AgentAction that encapsulates the consumer
     */
    public static AgenticScopeAction agentAction(AgenticScopeAction.NonThrowingConsumer<AgenticScope> consumer) {
        return new AgenticScopeAction(consumer);
    }

    public static class AgenticScopeAction {
        private final NonThrowingConsumer<AgenticScope> consumer;

        @FunctionalInterface
        public interface NonThrowingConsumer<T> {
            void accept(T arg) throws Exception;
        }

        private AgenticScopeAction(NonThrowingConsumer<AgenticScope> consumer) {
            this.consumer = consumer;
        }

        @Agent
        public void accept(AgenticScope agenticScope) {
            try {
                consumer.accept(agenticScope);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }
}
