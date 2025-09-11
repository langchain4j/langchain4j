package dev.langchain4j.agentic;

import dev.langchain4j.agentic.agent.AgentBuilder;
import dev.langchain4j.agentic.agent.ErrorContext;
import dev.langchain4j.agentic.agent.ErrorRecoveryResult;
import dev.langchain4j.agentic.declarative.ChatMemoryProviderSupplier;
import dev.langchain4j.agentic.declarative.ChatMemorySupplier;
import dev.langchain4j.agentic.declarative.ContentRetrieverSupplier;
import dev.langchain4j.agentic.declarative.ErrorHandler;
import dev.langchain4j.agentic.declarative.RetrievalAugmentorSupplier;
import dev.langchain4j.agentic.declarative.ToolProviderSupplier;
import dev.langchain4j.agentic.declarative.ToolsSupplier;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.agentic.declarative.ActivationCondition;
import dev.langchain4j.agentic.declarative.ConditionalAgent;
import dev.langchain4j.agentic.declarative.ParallelExecutor;
import dev.langchain4j.agentic.declarative.ExitCondition;
import dev.langchain4j.agentic.declarative.LoopAgent;
import dev.langchain4j.agentic.declarative.Output;
import dev.langchain4j.agentic.declarative.ParallelAgent;
import dev.langchain4j.agentic.declarative.SequenceAgent;
import dev.langchain4j.agentic.declarative.SubAgent;
import dev.langchain4j.agentic.declarative.ChatModelSupplier;
import dev.langchain4j.agentic.declarative.SupervisorRequest;
import dev.langchain4j.agentic.internal.A2AClientBuilder;
import dev.langchain4j.agentic.internal.A2AService;
import dev.langchain4j.agentic.internal.AgentExecutor;
import dev.langchain4j.agentic.internal.AgentSpecification;
import dev.langchain4j.agentic.internal.AgentInvoker;
import dev.langchain4j.agentic.supervisor.SupervisorAgent;
import dev.langchain4j.agentic.supervisor.SupervisorAgentService;
import dev.langchain4j.agentic.supervisor.SupervisorAgentServiceImpl;
import dev.langchain4j.agentic.workflow.ConditionalAgentService;
import dev.langchain4j.agentic.workflow.HumanInTheLoop;
import dev.langchain4j.agentic.workflow.LoopAgentService;
import dev.langchain4j.agentic.workflow.WorkflowService;
import dev.langchain4j.agentic.workflow.ParallelAgentService;
import dev.langchain4j.agentic.workflow.SequentialAgentService;
import dev.langchain4j.agentic.workflow.WorkflowAgentsBuilder;
import dev.langchain4j.agentic.workflow.impl.WorkflowAgentsBuilderImpl;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.service.tool.ToolProvider;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static dev.langchain4j.agentic.internal.AgentUtil.agentToExecutor;
import static dev.langchain4j.agentic.internal.AgentUtil.getAnnotatedMethodOnClass;
import static dev.langchain4j.agentic.internal.AgentUtil.methodInvocationArguments;
import static dev.langchain4j.agentic.internal.AgentUtil.validateAgentClass;
import static dev.langchain4j.internal.Utils.isNullOrBlank;

/**
 * Provides static factory methods to create and configure various types of agent services.
 */
public class AgenticServices {

    private AgenticServices() { }

    private enum WorkflowBuilderProvider {

        INSTANCE;

        private WorkflowAgentsBuilder workflowAgentsBuilder;

        WorkflowBuilderProvider() {
            internalSetWorkflowAgentsBuilder(loadWorkflowAgentsBuilder());
        }

        private static WorkflowAgentsBuilder loadWorkflowAgentsBuilder() {
            ServiceLoader<WorkflowAgentsBuilder> loader =
                    ServiceLoader.load(WorkflowAgentsBuilder.class);

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
     * Creates an agent builder for the given agent service class.
     *
     * @param agentServiceClass the class of the agent service
     * @return a new AgentBuilder instance
     */
    public static <T> AgentBuilder<T> agentBuilder(Class<T> agentServiceClass) {
        return new AgentBuilder<>(agentServiceClass, validateAgentClass(agentServiceClass));
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
            AgentBuilder<T> agentBuilder();
    }

    public record DefaultDeclarativeAgentCreationContext<T>(Class<T> agentServiceClass, AgentBuilder<T> agentBuilder) implements DeclarativeAgentCreationContext<T> { }

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
        return createAgenticSystem(agentServiceClass, chatModel, ctx -> { });
    }

    /**
     * Creates an instance of an agentic system defined through the declarative API.
     *
     * @param agentServiceClass the class of the agent service
     * @param agentConfigurator A callback to tweak the configuration of each agent created in this agentic system
     */
    public static <T> T createAgenticSystem(Class<T> agentServiceClass, Consumer<DeclarativeAgentCreationContext> agentConfigurator) {
        return createAgenticSystem(agentServiceClass, declarativeChatModel(agentServiceClass), agentConfigurator);
    }

    /**
     * Creates an instance of an agentic system defined through the declarative API and using the provided ChatModel.
     *
     * @param agentServiceClass the class of the agent service
     * @param chatModel the ChatModel used by default for all agents participating in this agentic system
     * @param agentConfigurator A callback to tweak the configuration of each agent created in this agentic system
     */
    public static <T> T createAgenticSystem(Class<T> agentServiceClass, ChatModel chatModel, Consumer<DeclarativeAgentCreationContext> agentConfigurator) {
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
        return selectMethod(agentServiceClass, method -> method.isAnnotationPresent(ChatModelSupplier.class) &&
                method.getReturnType() == ChatModel.class &&
                method.getParameterCount() == 0)
                .map(method -> (ChatModel) invokeStatic(method))
                .orElse(null);
    }

    private static <T> T createComposedAgent(Class<T> agentServiceClass, ChatModel chatModel, Consumer<DeclarativeAgentCreationContext> agentConfigurator) {
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

        Optional<Method> supervisorMethod = getAnnotatedMethodOnClass(agentServiceClass, dev.langchain4j.agentic.declarative.SupervisorAgent.class);
        if (supervisorMethod.isPresent()) {
            return buildSupervisorAgent(agentServiceClass, supervisorMethod.get(), chatModel, agentConfigurator);
        }

        return null;
    }

    private static <T> T buildSequentialAgent(Class<T> agentServiceClass, Method agentMethod, ChatModel chatModel, Consumer<DeclarativeAgentCreationContext> agentConfigurator) {
        SequenceAgent sequenceAgent = agentMethod.getAnnotation(SequenceAgent.class);
        var builder = sequenceBuilder(agentServiceClass)
                .subAgents(createSubagents(sequenceAgent.subAgents(), chatModel, agentConfigurator));

        buildAgentSpecs(agentServiceClass, agentMethod, sequenceAgent.name(), sequenceAgent.description(), sequenceAgent.outputName(), builder);
        buildErrorHandler(agentServiceClass).ifPresent(builder::errorHandler);

        return builder.build();
    }

    private static <T> T buildLoopAgent(Class<T> agentServiceClass, Method agentMethod, ChatModel chatModel, Consumer<DeclarativeAgentCreationContext> agentConfigurator) {
        LoopAgent loopAgent = agentMethod.getAnnotation(LoopAgent.class);
        var builder = loopBuilder(agentServiceClass)
                .subAgents(createSubagents(loopAgent.subAgents(), chatModel, agentConfigurator))
                .maxIterations(loopAgent.maxIterations());

        buildAgentSpecs(agentServiceClass, agentMethod, loopAgent.name(), loopAgent.description(), loopAgent.outputName(), builder);
        buildErrorHandler(agentServiceClass).ifPresent(builder::errorHandler);

        predicateMethod(agentServiceClass, method -> method.isAnnotationPresent(ExitCondition.class))
                .map(AgenticServices::agenticScopePredicate)
                .ifPresent(builder::exitCondition);

        return builder.build();
    }

    private static <T> T buildConditionalAgent(Class<T> agentServiceClass, Method agentMethod, ChatModel chatModel, Consumer<DeclarativeAgentCreationContext> agentConfigurator) {
        ConditionalAgent conditionalAgent = agentMethod.getAnnotation(ConditionalAgent.class);
        var builder = conditionalBuilder(agentServiceClass);

        buildAgentSpecs(agentServiceClass, agentMethod, conditionalAgent.name(), conditionalAgent.description(), conditionalAgent.outputName(), builder);
        buildErrorHandler(agentServiceClass).ifPresent(builder::errorHandler);

        for (SubAgent subagent : conditionalAgent.subAgents()) {
            predicateMethod(agentServiceClass, method -> {
                ActivationCondition activationCondition = method.getAnnotation(ActivationCondition.class);
                return activationCondition != null && Arrays.asList(activationCondition.value()).contains(subagent.type());
            })
                    .map(AgenticServices::agenticScopePredicate)
                    .ifPresent(condition -> builder.subAgent(condition, createSubagent(subagent, chatModel, agentConfigurator)));
        }

        return builder.build();
    }

    private static <T> T buildParallelAgent(Class<T> agentServiceClass, Method agentMethod, ChatModel chatModel, Consumer<DeclarativeAgentCreationContext> agentConfigurator) {
        ParallelAgent parallelAgent = agentMethod.getAnnotation(ParallelAgent.class);
        var builder = parallelBuilder(agentServiceClass)
                .subAgents(createSubagents(parallelAgent.subAgents(), chatModel, agentConfigurator));

        buildAgentSpecs(agentServiceClass, agentMethod, parallelAgent.name(), parallelAgent.description(), parallelAgent.outputName(), builder);
        buildErrorHandler(agentServiceClass).ifPresent(builder::errorHandler);

        selectMethod(agentServiceClass, method -> method.isAnnotationPresent(ParallelExecutor.class) &&
                Executor.class.isAssignableFrom(method.getReturnType()) &&
                method.getParameterCount() == 0)
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

    private static <T> void buildAgentSpecs(Class<T> agentServiceClass, Method agentMethod, String name, String description, String outputName, WorkflowService<?, ?> builder) {
        if (!isNullOrBlank(name)) {
            builder.name(name);
        } else {
            builder.name(agentMethod.getName());
        }
        if (!isNullOrBlank(description)) {
            builder.description(description);
        }
        if (!isNullOrBlank(outputName)) {
            builder.outputName(outputName);
        }

        selectMethod(agentServiceClass, method -> method.isAnnotationPresent(Output.class))
                .map(m -> AgenticServices.agenticScopeFunction(m, Object.class))
                .ifPresent(builder::output);
    }

    private static <T> T buildSupervisorAgent(Class<T> agentServiceClass, Method agentMethod, ChatModel chatModel, Consumer<DeclarativeAgentCreationContext> agentConfigurator) {
        dev.langchain4j.agentic.declarative.SupervisorAgent supervisorAgent = agentMethod.getAnnotation(dev.langchain4j.agentic.declarative.SupervisorAgent.class);
        var builder = supervisorBuilder(agentServiceClass)
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
        if (!isNullOrBlank(supervisorAgent.outputName())) {
            builder.outputName(supervisorAgent.outputName());
        }

        selectMethod(agentServiceClass, method -> method.isAnnotationPresent(SupervisorRequest.class) &&
                method.getReturnType() == String.class)
                .map(m -> AgenticServices.agenticScopeFunction(m, String.class))
                .ifPresent(builder::requestGenerator);

        selectMethod(agentServiceClass, method -> method.isAnnotationPresent(ChatModelSupplier.class) &&
                method.getReturnType() == ChatModel.class &&
                method.getParameterCount() == 0)
                .map(method -> (ChatModel) invokeStatic(method))
                .ifPresentOrElse(builder::chatModel, () -> builder.chatModel(chatModel));

        selectMethod(agentServiceClass, method -> method.isAnnotationPresent(Output.class))
                .map(m -> AgenticServices.agenticScopeFunction(m, Object.class))
                .ifPresent(builder::output);

        buildErrorHandler(agentServiceClass).ifPresent(builder::errorHandler);

        return builder.build();
    }

    private static <T> Optional<Function<ErrorContext, ErrorRecoveryResult>> buildErrorHandler(Class<T> agentServiceClass) {
        return selectMethod(agentServiceClass, method -> method.isAnnotationPresent(ErrorHandler.class))
                .map(m -> errorContext -> invokeStatic(m, errorContext));
    }

    private static Optional<Method> predicateMethod(Class<?> agentServiceClass, Predicate<Method> methodSelector) {
        return selectMethod(agentServiceClass, methodSelector.and(m -> (m.getReturnType() == boolean.class || m.getReturnType() == Boolean.class)));
    }

    private static Optional<Method> selectMethod(Class<?> agentServiceClass, Predicate<Method> methodSelector) {
        for (Method method : agentServiceClass.getMethods()) {
            if (methodSelector.test(method) && Modifier.isStatic(method.getModifiers())) {
                return Optional.of(method);
            }
        }
        return Optional.empty();
    }

    private static Predicate<AgenticScope> agenticScopePredicate(Method predicateMethod) {
        return agenticScope -> agenticScopeFunction(predicateMethod, boolean.class).apply(agenticScope);
    }

    private static <T> Function<AgenticScope, T> agenticScopeFunction(Method functionMethod, Class<T> targetClass) {
        boolean isAgenticScopeArg = functionMethod.getParameterCount() == 1 && functionMethod.getParameterTypes()[0] == AgenticScope.class;
        return agenticScope -> {
            try {
                Object[] args = isAgenticScopeArg ? new Object[] {agenticScope} : methodInvocationArguments(agenticScope, functionMethod);
                return (T) functionMethod.invoke(null, args);
            } catch (Exception e) {
                throw new RuntimeException("Error invoking exit condition method: " + functionMethod.getName(), e);
            }
        };
    }

    private static List<AgentExecutor> createSubagents(SubAgent[] subAgents, ChatModel chatModel, Consumer<DeclarativeAgentCreationContext> agentConfigurator) {
        return Stream.of(subAgents)
                .map(subagent -> createSubagent(subagent, chatModel, agentConfigurator))
                .toList();
    }

    private static AgentExecutor createSubagent(SubAgent subagent, ChatModel chatModel, Consumer<DeclarativeAgentCreationContext> agentConfigurator) {
        AgentExecutor agentExecutor = createComposedAgentExecutor(subagent.type(), chatModel, agentConfigurator);
        if (agentExecutor != null) {
            return agentExecutor;
        }

        AgentBuilder<?> agentBuilder = agentBuilder(subagent.type())
                .outputName(subagent.outputName());

        configureAgent(subagent.type(), chatModel, agentBuilder, agentConfigurator);

        if (subagent.summarizedContext() != null && subagent.summarizedContext().length > 0) {
            agentBuilder.summarizedContext(subagent.summarizedContext());
        }

        return agentToExecutor((AgentSpecification) agentBuilder.build());
    }

    private static void configureAgent(Class<?> agentType, ChatModel chatModel, AgentBuilder<?> agentBuilder, Consumer<DeclarativeAgentCreationContext> agentConfigurator) {
        getAnnotatedMethodOnClass(agentType, ToolsSupplier.class)
                .ifPresent(method -> {
                    checkArguments(method);
                    Object tools = invokeStatic(method);
                    if (tools.getClass().isArray()) {
                        agentBuilder.tools((Object[]) tools);
                    } else {
                        agentBuilder.tools(tools);
                    }
                });

        getAnnotatedMethodOnClass(agentType, ToolProviderSupplier.class)
                .ifPresent(method -> {
                    checkArguments(method);
                    checkReturnType(method, ToolProvider.class);
                    agentBuilder.toolProvider(invokeStatic(method));
                });

        getAnnotatedMethodOnClass(agentType, ContentRetrieverSupplier.class)
                .ifPresent(method -> {
                    checkArguments(method);
                    checkReturnType(method, ContentRetriever.class);
                    agentBuilder.contentRetriever(invokeStatic(method));
                });

        getAnnotatedMethodOnClass(agentType, RetrievalAugmentorSupplier.class)
                .ifPresent(method -> {
                    checkArguments(method);
                    checkReturnType(method, RetrievalAugmentor.class);
                    agentBuilder.retrievalAugmentor(invokeStatic(method));
                });

        getAnnotatedMethodOnClass(agentType, ChatMemoryProviderSupplier.class)
                .ifPresent(method -> {
                    checkArguments(method, Object.class);
                    checkReturnType(method, ChatMemory.class);
                    agentBuilder.chatMemoryProvider(memoryId -> invokeStatic(method, memoryId));
                });

        getAnnotatedMethodOnClass(agentType, ChatMemorySupplier.class)
                .ifPresent(method -> {
                    checkArguments(method);
                    checkReturnType(method, ChatMemory.class);
                    agentBuilder.chatMemory(invokeStatic(method));
                });

        getAnnotatedMethodOnClass(agentType, ChatModelSupplier.class)
                .ifPresentOrElse(method -> {
                            checkArguments(method);
                            checkReturnType(method, ChatModel.class);
                            agentBuilder.chatModel(invokeStatic(method));
                        },
                        () -> {
                            if (chatModel == null) {
                                throw new IllegalArgumentException("ChatModel not provided for subagent " + agentType.getName() +
                                        ". Please provide a ChatModel either through the @ChatModelSupplier annotation on a static method " +
                                        "or through the parent agent's chatModel parameter.");
                            }
                            agentBuilder.chatModel(chatModel);
                        });

        agentConfigurator.accept(new DefaultDeclarativeAgentCreationContext(agentType, agentBuilder));
    }

    private static <T> T invokeStatic(Method method, Object... args) {
        try {
            return (T) method.invoke(null, args);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }

    private static void checkArguments(Method method, Class<?>... expected) {
        Class<?>[] actual = method.getParameterTypes();
        if (actual.length != expected.length) {
            throw new IllegalArgumentException("Method " + method + " must have " + expected.length + " arguments: " + Arrays.toString(expected));
        }
        for (int i = 0; i < expected.length; i++) {
            if (!expected[i].isAssignableFrom(actual[i])) {
                throw new IllegalArgumentException("Method " + method + " argument " + (i + 1) + " must be of type " + expected[i].getName());
            }
        }
    }

    private static void checkReturnType(Method method, Class<?> expected) {
        if (!method.getReturnType().isAssignableFrom(expected)) {
            throw new IllegalArgumentException("Method " + method + " must return " + expected.getName());
        }
    }

    private static AgentExecutor createComposedAgentExecutor(Class<?> agentServiceClass, ChatModel chatModel, Consumer<DeclarativeAgentCreationContext> agentConfigurator) {
        Optional<Method> sequenceMethod = getAnnotatedMethodOnClass(agentServiceClass, SequenceAgent.class);
        if (sequenceMethod.isPresent()) {
            Method method = sequenceMethod.get();
            AgentSpecification agent = (AgentSpecification) buildSequentialAgent(agentServiceClass, method, chatModel, agentConfigurator);
            return new AgentExecutor(AgentInvoker.fromMethod(agent, method), agent);
        }

        Optional<Method> loopMethod = getAnnotatedMethodOnClass(agentServiceClass, LoopAgent.class);
        if (loopMethod.isPresent()) {
            Method method = loopMethod.get();
            AgentSpecification agent = (AgentSpecification) buildLoopAgent(agentServiceClass, method, chatModel, agentConfigurator);
            return new AgentExecutor(AgentInvoker.fromMethod(agent, method), agent);
        }

        Optional<Method> conditionalMethod = getAnnotatedMethodOnClass(agentServiceClass, ConditionalAgent.class);
        if (conditionalMethod.isPresent()) {
            Method method = conditionalMethod.get();
            AgentSpecification agent = (AgentSpecification) buildConditionalAgent(agentServiceClass, method, chatModel, agentConfigurator);
            return new AgentExecutor(AgentInvoker.fromMethod(agent, method), agent);
        }

        Optional<Method> parallelMethod = getAnnotatedMethodOnClass(agentServiceClass, ParallelAgent.class);
        if (parallelMethod.isPresent()) {
            Method method = parallelMethod.get();
            AgentSpecification agent = (AgentSpecification) buildParallelAgent(agentServiceClass, method, chatModel, agentConfigurator);
            return new AgentExecutor(AgentInvoker.fromMethod(agent, method), agent);
        }

        Optional<Method> supervisorMethod = getAnnotatedMethodOnClass(agentServiceClass, dev.langchain4j.agentic.declarative.SupervisorAgent.class);
        if (supervisorMethod.isPresent()) {
            Method method = supervisorMethod.get();
            AgentSpecification agent = (AgentSpecification) buildSupervisorAgent(agentServiceClass, method, chatModel, agentConfigurator);
            return new AgentExecutor(AgentInvoker.fromMethod(agent, method), agent);
        }

        return null;
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
