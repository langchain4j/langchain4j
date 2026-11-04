package dev.langchain4j.agentic.declarative;

import static dev.langchain4j.agentic.internal.AgentUtil.getAnnotatedMethodOnClass;
import static dev.langchain4j.agentic.internal.AgentUtil.validateAgentClass;

import dev.langchain4j.Internal;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.AgenticServices.DefaultDeclarativeAgentCreationContext;
import dev.langchain4j.agentic.AgenticServices.DefaultDeclarativeDecisionAgentCreationContext;
import dev.langchain4j.agentic.agent.AgentBuilder;
import dev.langchain4j.agentic.agent.DecisionAgentBuilder;
import dev.langchain4j.agentic.agent.ErrorContext;
import dev.langchain4j.agentic.agent.ErrorRecoveryResult;
import dev.langchain4j.agentic.internal.AgentHelpers;
import dev.langchain4j.agentic.internal.HelperMethod;
import dev.langchain4j.agentic.internal.ResolvedParameters;
import dev.langchain4j.agentic.observability.AgentListener;
import dev.langchain4j.agentic.planner.AgentCondition;
import dev.langchain4j.agentic.planner.AgenticService;
import dev.langchain4j.agentic.planner.AgenticSystemConfigurationException;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.decision.DecisionModel;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.service.decision.Decide;
import dev.langchain4j.service.decision.ThresholdContext;
import dev.langchain4j.service.tool.ToolExecutor;
import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.service.tool.search.ToolSearchStrategy;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

@Internal
public class DeclarativeUtil {

    private static final List<SupplierParameterResolver> supplierParameterResolvers = new CopyOnWriteArrayList<>();

    private DeclarativeUtil() {}

    /**
     * Applies the static helper methods of the given agent type to its builder, when the builder is created
     * programmatically: the chat model is left to the builder.
     */
    public static void configureAgent(Class<?> agentType, AgentBuilder<?, ?> agentBuilder) {
        configureAgent(
                AgentHelpers.staticOnly(agentType),
                null,
                true,
                agentBuilder,
                AgenticServices.AgentConfigurator.empty());
    }

    public static void configureAgent(
            Class<?> agentType,
            ChatModel chatModel,
            AgentBuilder<?, ?> agentBuilder,
            AgenticServices.AgentConfigurator agentConfigurator) {
        configureAgent(
                AgentHelpers.of(agentType, agentConfigurator.helperObjects(agentType)),
                chatModel,
                false,
                agentBuilder,
                agentConfigurator);
    }

    private static void configureAgent(
            AgentHelpers helpers,
            ChatModel chatModel,
            boolean allowNullChatModel,
            AgentBuilder<?, ?> agentBuilder,
            AgenticServices.AgentConfigurator agentConfigurator) {
        boolean chatModelSupplied = configureAgentFeatures(helpers, agentBuilder);
        if (!chatModelSupplied) {
            if (chatModel == null && !allowNullChatModel) {
                throw new IllegalArgumentException("ChatModel not provided for subagent "
                        + helpers.agentType().getName()
                        + ". Please provide one either with a static method annotated with @ChatModelSupplier "
                        + "or @StreamingChatModelSupplier, or through the parent agent's chatModel parameter.");
            }
            agentBuilder.chatModel(chatModel);
        }

        if (agentConfigurator.agentInstanceFactory() != null) {
            agentBuilder.agentInstanceFactory(agentConfigurator.agentInstanceFactory());
        }

        agentConfigurator
                .configurator()
                .accept(new DefaultDeclarativeAgentCreationContext(helpers.agentType(), agentBuilder));
    }

    /**
     * Applies the helpers of an AI agent to its builder.
     *
     * @return whether a chat model or a streaming chat model supplier has been applied
     */
    private static boolean configureAgentFeatures(AgentHelpers helpers, AgentBuilder<?, ?> agentBuilder) {
        Class<?> agentType = helpers.agentType();
        checkNoDecisionSuppliers(helpers);

        helpers.select(ToolsSupplier.class).ifPresent(helper -> {
            checkReturnType(helper.method(), Object.class);
            Object tools = invokeSupplierWithResolvers(agentType, helper, Object.class);
            if (tools instanceof Map) {
                agentBuilder.tools((Map<ToolSpecification, ToolExecutor>) tools);
            } else if (tools.getClass().isArray()) {
                agentBuilder.tools((Object[]) tools);
            } else {
                agentBuilder.tools(tools);
            }
        });

        helpers.select(ToolProviderSupplier.class).ifPresent(helper -> {
            checkReturnType(helper.method(), ToolProvider.class);
            agentBuilder.toolProvider(invokeSupplierWithResolvers(agentType, helper, ToolProvider.class));
        });

        helpers.select(ToolSearchStrategySupplier.class).ifPresent(helper -> {
            checkReturnType(helper.method(), ToolSearchStrategy.class);
            agentBuilder.toolSearchStrategy(invokeSupplierWithResolvers(agentType, helper, ToolSearchStrategy.class));
        });

        helpers.select(ContentRetrieverSupplier.class).ifPresent(helper -> {
            checkReturnType(helper.method(), ContentRetriever.class);
            agentBuilder.contentRetriever(invokeSupplierWithResolvers(agentType, helper, ContentRetriever.class));
        });

        helpers.select(RetrievalAugmentorSupplier.class).ifPresent(helper -> {
            checkReturnType(helper.method(), RetrievalAugmentor.class);
            agentBuilder.retrievalAugmentor(invokeSupplierWithResolvers(agentType, helper, RetrievalAugmentor.class));
        });

        helpers.select(ChatMemoryProviderSupplier.class).ifPresent(helper -> {
            checkReturnType(helper.method(), ChatMemory.class);
            ResolvedParameters parameters =
                    ResolvedParameters.of(agentType, helper).checkFrameworkParameters(Object.class);
            agentBuilder.chatMemoryProvider(memoryId -> parameters.invoke(memoryId));
        });

        helpers.select(ChatMemorySupplier.class).ifPresent(helper -> {
            checkReturnType(helper.method(), ChatMemory.class);
            agentBuilder.chatMemory(invokeSupplierWithResolvers(agentType, helper, ChatMemory.class));
        });

        // a supplier reading the AgenticScope is a function of it, invoked at each invocation of the agent; one whose
        // parameters are all provided by a resolver is a fixed model, invoked once
        boolean chatModelSupplied = false;
        Optional<HelperMethod> chatModelSupplier = helpers.select(ChatModelSupplier.class);
        if (chatModelSupplier.isPresent()) {
            HelperMethod helper = chatModelSupplier.get();
            checkReturnType(helper.method(), ChatModel.class);
            if (frameworkParameterCount(agentType, helper.method()) > 0) {
                agentBuilder.chatModel(agenticScopeFunction(agentType, helper, ChatModel.class));
            } else {
                agentBuilder.chatModel(invokeSupplierWithResolvers(agentType, helper, ChatModel.class));
            }
            chatModelSupplied = true;
        } else {
            Optional<HelperMethod> streamingChatModelSupplier = helpers.select(StreamingChatModelSupplier.class);
            if (streamingChatModelSupplier.isPresent()) {
                HelperMethod helper = streamingChatModelSupplier.get();
                checkReturnType(helper.method(), StreamingChatModel.class);
                if (frameworkParameterCount(agentType, helper.method()) > 0) {
                    agentBuilder.streamingChatModel(agenticScopeFunction(agentType, helper, StreamingChatModel.class));
                } else {
                    agentBuilder.streamingChatModel(
                            invokeSupplierWithResolvers(agentType, helper, StreamingChatModel.class));
                }
                chatModelSupplied = true;
            }
        }

        buildListener(helpers, agentBuilder::listener);

        helpers.select(SystemMessageProviderSupplier.class).ifPresent(helper -> {
            checkReturnType(helper.method(), String.class);
            ResolvedParameters parameters =
                    ResolvedParameters.of(agentType, helper).checkFrameworkParameters(Object.class);
            agentBuilder.systemMessageProvider(memoryId -> parameters.invoke(memoryId));
        });

        helpers.select(UserMessageProviderSupplier.class).ifPresent(helper -> {
            checkReturnType(helper.method(), String.class);
            ResolvedParameters parameters =
                    ResolvedParameters.of(agentType, helper).checkFrameworkParameters(Object.class);
            agentBuilder.userMessageProvider(memoryId -> parameters.invoke(memoryId));
        });

        AgentCondition activationCondition = activationCondition(helpers);
        if (activationCondition != null) {
            agentBuilder.activationCondition(activationCondition.description(), activationCondition.predicate());
        }
        return chatModelSupplied;
    }

    /**
     * Whether the given type is a decision agent: an interface whose {@link Agent @Agent}
     * method is annotated with {@link Decide @Decide}, or which declares a {@link DecisionModelSupplier} method.
     */
    public static boolean isDecisionAgent(Class<?> agentType) {
        if (!agentType.isInterface()) {
            return false;
        }
        Method agentMethod = validateAgentClass(agentType, false);
        if (agentMethod == null) {
            return false;
        }
        return agentMethod.isAnnotationPresent(Decide.class)
                || getAnnotatedMethodOnClass(agentType, DecisionModelSupplier.class)
                        .isPresent();
    }

    public static void configureDecisionAgent(Class<?> agentType, DecisionAgentBuilder<?> builder) {
        configureDecisionAgentFeatures(AgentHelpers.staticOnly(agentType), builder);
    }

    /**
     * Applies the declarative configuration of the given decision agent type to its builder: the
     * {@link DecisionModelSupplier}, the {@link DecisionThresholdSupplier}, the {@link AgentListenerSupplier} and the
     * {@link ActivationCondition}, static on the type or instance methods of its helper objects, and the agent
     * configurator.
     *
     * @throws AgenticSystemConfigurationException if the type also declares a {@link ChatModelSupplier} or a
     * {@link StreamingChatModelSupplier}
     */
    public static void configureDecisionAgent(
            Class<?> agentType, DecisionAgentBuilder<?> builder, AgenticServices.AgentConfigurator agentConfigurator) {
        configureDecisionAgentFeatures(AgentHelpers.of(agentType, agentConfigurator.helperObjects(agentType)), builder);

        if (agentConfigurator.agentInstanceFactory() != null) {
            builder.agentInstanceFactory(agentConfigurator.agentInstanceFactory());
        }

        agentConfigurator.configurator().accept(new DefaultDeclarativeDecisionAgentCreationContext(agentType, builder));
    }

    private static void configureDecisionAgentFeatures(AgentHelpers helpers, DecisionAgentBuilder<?> builder) {
        Class<?> agentType = helpers.agentType();
        for (Class<? extends Annotation> chatModelAnnotation :
                List.of(ChatModelSupplier.class, StreamingChatModelSupplier.class)) {
            if (helpers.select(chatModelAnnotation).isPresent()) {
                throw new AgenticSystemConfigurationException("The decision agent " + agentType.getName()
                        + " declares a method annotated with @" + chatModelAnnotation.getSimpleName()
                        + ": a decision agent uses a decision model, provided with @DecisionModelSupplier, and"
                        + " no chat model");
            }
        }

        helpers.select(DecisionModelSupplier.class).ifPresent(helper -> {
            checkReturnType(helper.method(), DecisionModel.class);
            if (frameworkParameterCount(agentType, helper.method()) > 0) {
                builder.decisionModelProvider(agenticScopeFunction(agentType, helper, DecisionModel.class));
            } else {
                builder.decisionModel(invokeSupplierWithResolvers(agentType, helper, DecisionModel.class));
            }
        });

        helpers.select(DecisionThresholdSupplier.class).ifPresent(helper -> {
            Method method = helper.method();
            if (method.getReturnType() != double.class && method.getReturnType() != Double.class) {
                throw new IllegalArgumentException("Method " + method + " must return double");
            }
            ResolvedParameters threshold =
                    ResolvedParameters.of(agentType, helper).checkFrameworkParameters(ThresholdContext.class);
            builder.thresholdProvider(context -> threshold.invoke(context));
        });

        buildListener(helpers, builder::listener);

        AgentCondition activationCondition = activationCondition(helpers);
        if (activationCondition != null) {
            builder.activationCondition(activationCondition.description(), activationCondition.predicate());
        }
    }

    /**
     * Checks that the given type, built as an agent of another kind, declares no static method annotated with
     * {@link DecisionModelSupplier} or {@link DecisionThresholdSupplier}, which would be silently ignored.
     *
     * @throws AgenticSystemConfigurationException if it does
     */
    public static void checkNoDecisionSuppliers(Class<?> agentType) {
        checkNoDecisionSuppliers(AgentHelpers.staticOnly(agentType));
    }

    /**
     * Checks that the given agent, built as an agent of another kind, has no helper method annotated with
     * {@link DecisionModelSupplier} or {@link DecisionThresholdSupplier}, which would be silently ignored.
     *
     * @throws AgenticSystemConfigurationException if it has
     */
    public static void checkNoDecisionSuppliers(AgentHelpers helpers) {
        for (Class<? extends Annotation> decisionAnnotation :
                List.of(DecisionModelSupplier.class, DecisionThresholdSupplier.class)) {
            if (helpers.select(decisionAnnotation).isPresent()) {
                throw new AgenticSystemConfigurationException("The agent "
                        + helpers.agentType().getName()
                        + " declares a method annotated with @" + decisionAnnotation.getSimpleName()
                        + ", but it is not built as a decision agent: a decision agent is an interface with an @Agent"
                        + " method annotated with @Decide, or declaring a @DecisionModelSupplier, built with"
                        + " AgenticServices.decisionAgentBuilder or by the declarative API");
            }
        }
    }

    /**
     * Checks that the given method can return an instance of the expected type: its return type must be a subtype or
     * a supertype, like {@code Object}, of the expected one.
     *
     * @throws IllegalArgumentException if it cannot
     */
    public static void checkReturnType(Method method, Class<?> expected) {
        Class<?> returnType = method.getReturnType();
        if (!expected.isAssignableFrom(returnType) && !returnType.isAssignableFrom(expected)) {
            throw new IllegalArgumentException("Method " + method + " must return " + expected.getName());
        }
    }

    public static <T> T invokeStatic(Method method, Object... args) {
        try {
            return (T) method.invoke(null, args);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }

    public static <T> void configureOutput(Class<T> agentServiceClass, AgenticService<?, ?> builder) {
        configureOutput(AgentHelpers.staticOnly(agentServiceClass), builder);
    }

    public static void configureOutput(AgentHelpers helpers, AgenticService<?, ?> builder) {
        helpers.select(Output.class)
                .map(helper -> agenticScopeFunction(helpers.agentType(), helper, Object.class))
                .ifPresent(builder::output);
    }

    /**
     * Selects the static condition method matching the given selector.
     *
     * @throws IllegalArgumentException if the selected method doesn't return a boolean
     */
    public static Optional<Method> predicateMethod(Class<?> agentServiceClass, Predicate<Method> methodSelector) {
        Optional<Method> method = selectMethod(agentServiceClass, methodSelector);
        method.ifPresent(DeclarativeUtil::checkBooleanReturnType);
        return method;
    }

    /**
     * Selects the condition helper method annotated with the given annotation, a static method of the agent type or
     * an instance method of a helper object.
     *
     * @throws IllegalArgumentException if the selected method doesn't return a boolean
     */
    public static Optional<HelperMethod> predicateHelper(AgentHelpers helpers, Class<? extends Annotation> annotation) {
        return predicateHelper(helpers, annotation, method -> true);
    }

    /**
     * Selects the condition helper method annotated with the given annotation and matching the given filter, a
     * static method of the agent type or an instance method of a helper object.
     *
     * @throws IllegalArgumentException if the selected method doesn't return a boolean
     */
    public static Optional<HelperMethod> predicateHelper(
            AgentHelpers helpers, Class<? extends Annotation> annotation, Predicate<Method> filter) {
        Optional<HelperMethod> helper = helpers.select(annotation, filter);
        helper.ifPresent(h -> checkBooleanReturnType(h.method()));
        return helper;
    }

    private static void checkBooleanReturnType(Method method) {
        if (method.getReturnType() != boolean.class && method.getReturnType() != Boolean.class) {
            throw new IllegalArgumentException("Method " + method + " must return boolean");
        }
    }

    public static void buildAgentFeatures(Class<?> agentServiceClass, AgenticService<?, ?> builder) {
        buildAgentFeatures(AgentHelpers.staticOnly(agentServiceClass), builder);
    }

    /**
     * Applies to the builder of a workflow, planner or supervisor agent the helpers common to every kind of them:
     * the {@link BeforeCall}, the {@link ErrorHandler}, the {@link AgentListenerSupplier} and the
     * {@link ActivationCondition} without value.
     */
    public static void buildAgentFeatures(AgentHelpers helpers, AgenticService<?, ?> builder) {
        checkNoDecisionSuppliers(helpers);
        buildBeforeCall(helpers).ifPresent(builder::beforeCall);
        buildErrorHandler(helpers).ifPresent(builder::errorHandler);
        buildListener(helpers, builder::listener);
        AgentCondition activationCondition = activationCondition(helpers);
        if (activationCondition != null) {
            builder.activationCondition(activationCondition.description(), activationCondition.predicate());
        }
    }

    /**
     * Reads the activation condition of the agent declared by the given class or interface: the static method
     * annotated with {@link ActivationCondition} without a value. An {@link ActivationCondition} with a value is
     * allowed only on a {@link ConditionalAgent}, where it selects the subagents to activate, and where a condition
     * without value is not allowed.
     *
     * @return the activation condition of the agent, or {@code null} when it has none
     * @throws AgenticSystemConfigurationException if there is more than one such method, if it doesn't return a
     *         boolean, or if a condition with a value is declared on a class that is not a conditional agent
     */
    public static AgentCondition activationCondition(Class<?> agentClass) {
        return activationCondition(AgentHelpers.staticOnly(agentClass));
    }

    /**
     * Reads the activation condition of the given agent: the method annotated with {@link ActivationCondition}
     * without a value, static on the agent type or an instance method of one of its helper objects. An
     * {@link ActivationCondition} with a value is allowed only on a {@link ConditionalAgent}, where it selects the
     * subagents to activate, and where a condition without value is not allowed.
     *
     * @return the activation condition of the agent, or {@code null} when it has none, or when it is a static method
     *         already applied
     * @throws AgenticSystemConfigurationException if there is more than one such method, if it doesn't return a
     *         boolean, or if a condition with a value is declared on a class that is not a conditional agent
     */
    public static AgentCondition activationCondition(AgentHelpers helpers) {
        Class<?> agentClass = helpers.agentType();
        boolean conditionalAgent =
                getAnnotatedMethodOnClass(agentClass, ConditionalAgent.class).isPresent();
        HelperMethod conditionMethod = null;
        for (HelperMethod helper : helpers.selectAll(ActivationCondition.class)) {
            Method method = helper.method();
            if (method.getAnnotation(ActivationCondition.class).value().length > 0) {
                if (!conditionalAgent) {
                    throw new AgenticSystemConfigurationException("The @ActivationCondition method " + method
                            + " names the subagents it activates, but " + agentClass.getName()
                            + " is not a @ConditionalAgent. Remove its value to make it the activation condition of "
                            + agentClass.getSimpleName() + " itself.");
                }
                continue;
            }
            if (conditionalAgent) {
                throw new AgenticSystemConfigurationException("The @ActivationCondition method " + method
                        + " has no value, but on the @ConditionalAgent " + agentClass.getName()
                        + " every activation condition must name the subagents it activates.");
            }
            if (method.getReturnType() != boolean.class && method.getReturnType() != Boolean.class) {
                throw new AgenticSystemConfigurationException(
                        "The @ActivationCondition method " + method + " must return a boolean");
            }
            if (conditionMethod != null) {
                throw new AgenticSystemConfigurationException("Only one @ActivationCondition method without value is "
                        + "allowed in " + agentClass.getName() + ", but found "
                        + conditionMethod.method().getName() + " and " + method.getName());
            }
            conditionMethod = helper;
        }
        return conditionMethod == null || !helpers.isApplicable(conditionMethod)
                ? null
                : new AgentCondition(
                        conditionMethod
                                .method()
                                .getAnnotation(ActivationCondition.class)
                                .description(),
                        agenticScopeFunction(agentClass, conditionMethod, Boolean.class, false)::apply);
    }

    /**
     * The static methods annotated with the given annotation declared by the given type, its superclasses and its
     * interfaces.
     */
    public static Set<Method> staticMethodsAnnotatedWith(Class<?> type, Class<? extends Annotation> annotation) {
        Set<Method> methods = new LinkedHashSet<>();
        collectStaticMethodsAnnotatedWith(type, annotation, methods);
        return methods;
    }

    private static void collectStaticMethodsAnnotatedWith(
            Class<?> type, Class<? extends Annotation> annotation, Set<Method> methods) {
        for (Method method : type.getMethods()) {
            if (method.isAnnotationPresent(annotation) && Modifier.isStatic(method.getModifiers())) {
                methods.add(method);
            }
        }
        if (type.getSuperclass() != null) {
            collectStaticMethodsAnnotatedWith(type.getSuperclass(), annotation, methods);
        }
        for (Class<?> interf : type.getInterfaces()) {
            collectStaticMethodsAnnotatedWith(interf, annotation, methods);
        }
    }

    private static Optional<Consumer<AgenticScope>> buildBeforeCall(AgentHelpers helpers) {
        return helpers.select(BeforeCall.class).map(helper -> {
            checkReturnType(helper.method(), void.class);
            return agenticScopeFunction(helpers.agentType(), helper, Object.class)::apply;
        });
    }

    public static Optional<Executor> parallelExecutor(Class<?> agentServiceClass) {
        return parallelExecutor(AgentHelpers.staticOnly(agentServiceClass));
    }

    public static Optional<Executor> parallelExecutor(AgentHelpers helpers) {
        return helpers.select(ParallelExecutor.class).map(helper -> {
            checkReturnType(helper.method(), Executor.class);
            return invokeSupplierWithResolvers(helpers.agentType(), helper, Executor.class);
        });
    }

    private static Optional<Function<ErrorContext, ErrorRecoveryResult>> buildErrorHandler(AgentHelpers helpers) {
        return helpers.select(ErrorHandler.class).map(helper -> {
            checkReturnType(helper.method(), ErrorRecoveryResult.class);
            ResolvedParameters parameters =
                    ResolvedParameters.of(helpers.agentType(), helper).checkFrameworkParameters(ErrorContext.class);
            return errorContext -> parameters.invoke(errorContext);
        });
    }

    /**
     * Applies the {@link AgentListenerSupplier} helper of the given agent, if any, to the given listener registration
     * of its builder, which composes the listener with the ones already registered.
     */
    public static void buildListener(AgentHelpers helpers, Consumer<AgentListener> listenerRegistration) {
        helpers.select(AgentListenerSupplier.class).ifPresent(helper -> {
            checkReturnType(helper.method(), AgentListener.class);
            listenerRegistration.accept(invokeSupplierWithResolvers(helpers.agentType(), helper, AgentListener.class));
        });
    }

    public static Optional<Method> selectMethod(Class<?> agentServiceClass, Predicate<Method> methodSelector) {
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

    public static Predicate<AgenticScope> agenticScopePredicate(Method predicateMethod) {
        return agenticScopePredicate(predicateMethod.getDeclaringClass(), predicateMethod);
    }

    public static Predicate<AgenticScope> agenticScopePredicate(Class<?> agentType, Method predicateMethod) {
        return agenticScopeFunction(agentType, predicateMethod, boolean.class)::apply;
    }

    public static Predicate<AgenticScope> agenticScopePredicate(Class<?> agentType, HelperMethod predicateMethod) {
        return agenticScopeFunction(agentType, predicateMethod, boolean.class)::apply;
    }

    /**
     * @return the number of parameters of the given method that are not provided by a registered
     *         {@link SupplierParameterResolver}
     */
    public static int frameworkParameterCount(Class<?> agentType, Method method) {
        return method.getParameterCount() == 0
                ? 0
                : ResolvedParameters.of(agentType, method).frameworkParameterCount();
    }

    /**
     * Invokes a static supplier method: all its parameters must be provided by the registered
     * {@link SupplierParameterResolver}s.
     *
     * @throws AgenticSystemConfigurationException if a parameter is not supported by any resolver
     */
    public static <T> T invokeSupplierWithResolvers(Class<?> agentType, Method method, Class<T> targetClass) {
        return invokeSupplierWithResolvers(agentType, HelperMethod.ofStatic(method), targetClass);
    }

    /**
     * Invokes a supplier method, a static method of the agent type or an instance method of a helper object: all its
     * parameters must be provided by the registered {@link SupplierParameterResolver}s.
     *
     * @throws AgenticSystemConfigurationException if a parameter is not supported by any resolver
     */
    public static <T> T invokeSupplierWithResolvers(Class<?> agentType, HelperMethod helper, Class<T> targetClass) {
        if (helper.method().getParameterCount() == 0) {
            return helper.invoke();
        }
        return ResolvedParameters.of(agentType, helper).checkAllResolved().invoke();
    }

    public static <T> Function<AgenticScope, T> agenticScopeFunction(Method functionMethod, Class<T> targetClass) {
        return agenticScopeFunction(functionMethod.getDeclaringClass(), functionMethod, targetClass);
    }

    /**
     * Creates a function invoking the given static method declared on, or inherited by, the given agent type: its
     * parameters supported by a registered {@link SupplierParameterResolver} are resolved, the others are read from
     * the {@link AgenticScope}, and are {@code null} when the function is applied without one.
     */
    public static <T> Function<AgenticScope, T> agenticScopeFunction(
            Class<?> agentType, Method functionMethod, Class<T> targetClass) {
        return agenticScopeFunction(agentType, HelperMethod.ofStatic(functionMethod), targetClass, true);
    }

    /**
     * Creates a function invoking the given helper method of the given agent type, a static method of the type or an
     * instance method of a helper object: its parameters supported by a registered {@link SupplierParameterResolver}
     * are resolved, the others are read from the {@link AgenticScope}, and are {@code null} when the function is
     * applied without one.
     */
    public static <T> Function<AgenticScope, T> agenticScopeFunction(
            Class<?> agentType, HelperMethod functionMethod, Class<T> targetClass) {
        return agenticScopeFunction(agentType, functionMethod, targetClass, true);
    }

    private static <T> Function<AgenticScope, T> agenticScopeFunction(
            Class<?> agentType, HelperMethod helper, Class<T> targetClass, boolean writeConvertedArguments) {
        ResolvedParameters parameters = ResolvedParameters.of(agentType, helper);
        Method functionMethod = helper.method();
        return agenticScope -> {
            try {
                Object[] args = parameters
                        .invocationArguments(agenticScope, Map.of(), writeConvertedArguments)
                        .positionalArgs();
                return (T) functionMethod.invoke(helper.target(), args);
            } catch (Exception e) {
                throw new RuntimeException("Error invoking method: " + functionMethod.getName(), e);
            }
        };
    }

    public static void addSupplierParameterResolver(SupplierParameterResolver resolver) {
        supplierParameterResolvers.add(resolver);
    }

    public static List<SupplierParameterResolver> getSupplierParameterResolvers() {
        return supplierParameterResolvers;
    }
}
