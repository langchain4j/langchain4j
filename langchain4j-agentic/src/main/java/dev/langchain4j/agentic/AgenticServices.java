package dev.langchain4j.agentic;

import dev.langchain4j.agentic.agent.AgentBuilder;
import dev.langchain4j.agentic.agent.ErrorContext;
import dev.langchain4j.agentic.agent.ErrorRecoveryResult;
import dev.langchain4j.agentic.declarative.ErrorHandler;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.agentic.declarative.ActivationCondition;
import dev.langchain4j.agentic.declarative.ConditionalAgent;
import dev.langchain4j.agentic.declarative.ExecutorService;
import dev.langchain4j.agentic.declarative.ExitCondition;
import dev.langchain4j.agentic.declarative.LoopAgent;
import dev.langchain4j.agentic.declarative.Output;
import dev.langchain4j.agentic.declarative.ParallelAgent;
import dev.langchain4j.agentic.declarative.SequenceAgent;
import dev.langchain4j.agentic.declarative.SubAgent;
import dev.langchain4j.agentic.declarative.SupervisorChatModel;
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
import dev.langchain4j.model.chat.ChatModel;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.ServiceLoader;
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

    /**
     * Creates an instance of an agentic system defined through the declarative API.
     */
    public static <T> T createAgenticSystem(Class<T> agentServiceClass, ChatModel chatModel) {
        T agent = createComposedAgent(agentServiceClass, chatModel);

        if (agent == null) {
            throw new IllegalArgumentException("Provided class " + agentServiceClass.getName() + " is not an agent.");
        }

        return agent;
    }

    private static <T> T createComposedAgent(Class<T> agentServiceClass, ChatModel chatModel) {
        Optional<Method> sequenceMethod = getAnnotatedMethodOnClass(agentServiceClass, SequenceAgent.class);
        if (sequenceMethod.isPresent()) {
            return buildSequentialAgent(agentServiceClass, sequenceMethod.get(), chatModel);
        }

        Optional<Method> loopMethod = getAnnotatedMethodOnClass(agentServiceClass, LoopAgent.class);
        if (loopMethod.isPresent()) {
            return buildLoopAgent(agentServiceClass, loopMethod.get(), chatModel);
        }

        Optional<Method> conditionalMethod = getAnnotatedMethodOnClass(agentServiceClass, ConditionalAgent.class);
        if (conditionalMethod.isPresent()) {
            return buildConditionalAgent(agentServiceClass, conditionalMethod.get(), chatModel);
        }

        Optional<Method> parallelMethod = getAnnotatedMethodOnClass(agentServiceClass, ParallelAgent.class);
        if (parallelMethod.isPresent()) {
            return buildParallelAgent(agentServiceClass, parallelMethod.get(), chatModel);
        }

        Optional<Method> supervisorMethod = getAnnotatedMethodOnClass(agentServiceClass, dev.langchain4j.agentic.declarative.SupervisorAgent.class);
        if (supervisorMethod.isPresent()) {
            return buildSupervisorAgent(agentServiceClass, supervisorMethod.get(), chatModel);
        }

         return null;
    }

    private static <T> T buildSequentialAgent(Class<T> agentServiceClass, Method agentMethod, ChatModel chatModel) {
        SequenceAgent sequenceAgent = agentMethod.getAnnotation(SequenceAgent.class);
        var builder = sequenceBuilder(agentServiceClass)
                .subAgents(createSubagents(sequenceAgent.subAgents(), chatModel));

        buildOutput(agentServiceClass, sequenceAgent.outputName(), builder);
        buildErrorHandler(agentServiceClass).ifPresent(builder::errorHandler);

        return builder.build();
    }

    private static <T> T buildLoopAgent(Class<T> agentServiceClass, Method agentMethod, ChatModel chatModel) {
        LoopAgent loopAgent = agentMethod.getAnnotation(LoopAgent.class);
        var builder = loopBuilder(agentServiceClass)
                .subAgents(createSubagents(loopAgent.subAgents(), chatModel))
                .maxIterations(loopAgent.maxIterations());

        buildOutput(agentServiceClass, loopAgent.outputName(), builder);
        buildErrorHandler(agentServiceClass).ifPresent(builder::errorHandler);

        predicateMethod(agentServiceClass, method -> method.isAnnotationPresent(ExitCondition.class))
                .map(AgenticServices::agenticScopePredicate)
                .ifPresent(builder::exitCondition);

        return builder.build();
    }

    private static <T> T buildConditionalAgent(Class<T> agentServiceClass, Method agentMethod, ChatModel chatModel) {
        ConditionalAgent conditionalAgent = agentMethod.getAnnotation(ConditionalAgent.class);
        var builder = conditionalBuilder(agentServiceClass);

        buildOutput(agentServiceClass, conditionalAgent.outputName(), builder);
        buildErrorHandler(agentServiceClass).ifPresent(builder::errorHandler);

        for (SubAgent subagent : conditionalAgent.subAgents()) {
            predicateMethod(agentServiceClass, method -> {
                ActivationCondition activationCondition = method.getAnnotation(ActivationCondition.class);
                return activationCondition != null && Arrays.asList(activationCondition.value()).contains(subagent.type());
            })
                    .map(AgenticServices::agenticScopePredicate)
                    .ifPresent(condition -> builder.subAgent(condition, createSubagent(subagent, chatModel)));
        }

        return builder.build();
    }

    private static <T> T buildParallelAgent(Class<T> agentServiceClass, Method agentMethod, ChatModel chatModel) {
        ParallelAgent parallelAgent = agentMethod.getAnnotation(ParallelAgent.class);
        var builder = parallelBuilder(agentServiceClass)
                .subAgents(createSubagents(parallelAgent.subAgents(), chatModel));

        buildOutput(agentServiceClass, parallelAgent.outputName(), builder);
        buildErrorHandler(agentServiceClass).ifPresent(builder::errorHandler);

        selectMethod(agentServiceClass, method -> method.isAnnotationPresent(ExecutorService.class) &&
                method.getReturnType() == java.util.concurrent.ExecutorService.class &&
                method.getParameterCount() == 0)
                .map(method -> {
                    try {
                        return (java.util.concurrent.ExecutorService) method.invoke(null);
                    } catch (Exception e) {
                        throw new RuntimeException("Error invoking executor method: " + method.getName(), e);
                    }
                })
                .ifPresent(builder::executorService);

        return builder.build();
    }

    private static <T> T buildSupervisorAgent(Class<T> agentServiceClass, Method agentMethod, ChatModel chatModel) {
        dev.langchain4j.agentic.declarative.SupervisorAgent supervisorAgent = agentMethod.getAnnotation(dev.langchain4j.agentic.declarative.SupervisorAgent.class);
        var builder = supervisorBuilder(agentServiceClass)
                .maxAgentsInvocations(supervisorAgent.maxAgentsInvocations())
                .contextGenerationStrategy(supervisorAgent.contextStrategy())
                .responseStrategy(supervisorAgent.responseStrategy())
                .subAgents(createSubagents(supervisorAgent.subAgents(), chatModel));

        if (!supervisorAgent.outputName().isBlank()) {
            builder.outputName(supervisorAgent.outputName());
        }

        selectMethod(agentServiceClass, method -> method.isAnnotationPresent(SupervisorRequest.class) &&
                method.getReturnType() == String.class)
                .map(m -> AgenticServices.agenticScopeFunction(m, String.class))
                .ifPresent(builder::requestGenerator);

        selectMethod(agentServiceClass, method -> method.isAnnotationPresent(SupervisorChatModel.class) &&
                method.getReturnType() == ChatModel.class &&
                method.getParameterCount() == 0)
                .map(method -> {
                    try {
                        return (ChatModel) method.invoke(null);
                    } catch (Exception e) {
                        throw new RuntimeException("Error invoking executor method: " + method.getName(), e);
                    }
                })
                .ifPresentOrElse(builder::chatModel, () -> builder.chatModel(chatModel));

        buildErrorHandler(agentServiceClass).ifPresent(builder::errorHandler);

        return builder.build();
    }

    private static <T> void buildOutput(Class<T> agentServiceClass, String outputName, WorkflowService<?, ?> builder) {
        if (!outputName.isBlank()) {
            builder.outputName(outputName);
        }

        selectMethod(agentServiceClass, method -> method.isAnnotationPresent(Output.class))
                .map(m -> AgenticServices.agenticScopeFunction(m, Object.class))
                .ifPresent(builder::output);
    }

    private static <T> Optional<Function<ErrorContext, ErrorRecoveryResult>> buildErrorHandler(Class<T> agentServiceClass) {
        return selectMethod(agentServiceClass, method -> method.isAnnotationPresent(ErrorHandler.class))
                .map(m -> (Function<ErrorContext, ErrorRecoveryResult>) errorContext -> {
                    try {
                        return (ErrorRecoveryResult) m.invoke(null, errorContext);
                    } catch (IllegalAccessException | InvocationTargetException e) {
                        throw new RuntimeException(e);
                    }
                });
    }

    private static Optional<Method> predicateMethod(Class<?> agentServiceClass, Predicate<Method> methodSelector) {
        return selectMethod(agentServiceClass, methodSelector.and(m -> (m.getReturnType() == boolean.class || m.getReturnType() == Boolean.class)));
    }

    private static Optional<Method> selectMethod(Class<?> agentServiceClass, Predicate<Method> methodSelector) {
        for (Method method : agentServiceClass.getDeclaredMethods()) {
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

    private static List<AgentExecutor> createSubagents(SubAgent[] subAgents, ChatModel chatModel) {
        return Stream.of(subAgents)
                .map(subagent -> createSubagent(subagent, chatModel))
                .toList();
    }

    private static AgentExecutor createSubagent(SubAgent subagent, ChatModel chatModel) {
        AgentExecutor agentExecutor = createComposedAgentExecutor(subagent.type(), chatModel);
        if (agentExecutor != null) {
            return agentExecutor;
        }

        return agentToExecutor((AgentSpecification) AgenticServices.agentBuilder(subagent.type())
                .chatModel(chatModel)
                .outputName(subagent.outputName())
                .build());
    }

    private static AgentExecutor createComposedAgentExecutor(Class<?> agentServiceClass, ChatModel chatModel) {
        Optional<Method> sequenceMethod = getAnnotatedMethodOnClass(agentServiceClass, SequenceAgent.class);
        if (sequenceMethod.isPresent()) {
            Method method = sequenceMethod.get();
            AgentSpecification agent = (AgentSpecification) buildSequentialAgent(agentServiceClass, method, chatModel);
            SequenceAgent annotation = method.getAnnotation(SequenceAgent.class);
            String name = annotation == null || isNullOrBlank(annotation.name()) ? method.getName() : annotation.name();
            String description = annotation == null ? "" : String.join("\n", annotation.description());
            return new AgentExecutor(AgentInvoker.fromMethodAndSpec(method, name, description, agent.outputName()), agent);
        }

        Optional<Method> loopMethod = getAnnotatedMethodOnClass(agentServiceClass, LoopAgent.class);
        if (loopMethod.isPresent()) {
            Method method = loopMethod.get();
            AgentSpecification agent = (AgentSpecification) buildLoopAgent(agentServiceClass, loopMethod.get(), chatModel);
            LoopAgent annotation = method.getAnnotation(LoopAgent.class);
            String name = annotation == null || isNullOrBlank(annotation.name()) ? method.getName() : annotation.name();
            String description = annotation == null ? "" : String.join("\n", annotation.description());
            return new AgentExecutor(AgentInvoker.fromMethodAndSpec(method, name, description, agent.outputName()), agent);
        }

        Optional<Method> conditionalMethod = getAnnotatedMethodOnClass(agentServiceClass, ConditionalAgent.class);
        if (conditionalMethod.isPresent()) {
            Method method = conditionalMethod.get();
            AgentSpecification agent = (AgentSpecification) buildConditionalAgent(agentServiceClass, conditionalMethod.get(), chatModel);
            ConditionalAgent annotation = method.getAnnotation(ConditionalAgent.class);
            String name = annotation == null || isNullOrBlank(annotation.name()) ? method.getName() : annotation.name();
            String description = annotation == null ? "" : String.join("\n", annotation.description());
            return new AgentExecutor(AgentInvoker.fromMethodAndSpec(method, name, description, agent.outputName()), agent);
        }

        Optional<Method> parallelMethod = getAnnotatedMethodOnClass(agentServiceClass, ParallelAgent.class);
        if (parallelMethod.isPresent()) {
            Method method = parallelMethod.get();
            AgentSpecification agent = (AgentSpecification) buildParallelAgent(agentServiceClass, parallelMethod.get(), chatModel);
            ParallelAgent annotation = method.getAnnotation(ParallelAgent.class);
            String name = annotation == null || isNullOrBlank(annotation.name()) ? method.getName() : annotation.name();
            String description = annotation == null ? "" : String.join("\n", annotation.description());
            return new AgentExecutor(AgentInvoker.fromMethodAndSpec(method, name, description, agent.outputName()), agent);
        }

        Optional<Method> supervisorMethod = getAnnotatedMethodOnClass(agentServiceClass, dev.langchain4j.agentic.declarative.SupervisorAgent.class);
        if (supervisorMethod.isPresent()) {
            Method method = supervisorMethod.get();
            AgentSpecification agent = (AgentSpecification) buildSupervisorAgent(agentServiceClass, supervisorMethod.get(), chatModel);
            dev.langchain4j.agentic.declarative.SupervisorAgent annotation = method.getAnnotation(dev.langchain4j.agentic.declarative.SupervisorAgent.class);
            String name = annotation == null || isNullOrBlank(annotation.name()) ? method.getName() : annotation.name();
            String description = annotation == null ? "" : String.join("\n", annotation.description());
            return new AgentExecutor(AgentInvoker.fromMethodAndSpec(method, name, description, agent.outputName()), agent);
        }

        return null;
    }
}
