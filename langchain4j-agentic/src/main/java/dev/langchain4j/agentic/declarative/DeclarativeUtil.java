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

    public static void configureAgent(Class<?> agentType, AgentBuilder<?, ?> agentBuilder) {
        configureAgent(agentType, null, true, agentBuilder, AgenticServices.AgentConfigurator.empty());
    }

    public static void configureAgent(
            Class<?> agentType,
            ChatModel chatModel,
            AgentBuilder<?, ?> agentBuilder,
            AgenticServices.AgentConfigurator agentConfigurator) {
        configureAgent(agentType, chatModel, false, agentBuilder, agentConfigurator);
    }

    private static void configureAgent(
            Class<?> agentType,
            ChatModel chatModel,
            boolean allowNullChatModel,
            AgentBuilder<?, ?> agentBuilder,
            AgenticServices.AgentConfigurator agentConfigurator) {
        checkNoDecisionSuppliers(agentType);

        getAnnotatedMethodOnClass(agentType, ToolsSupplier.class).ifPresent(method -> {
            checkReturnType(method, Object.class);
            Object tools = invokeSupplierWithResolvers(agentType, method, Object.class);
            if (tools instanceof Map) {
                agentBuilder.tools((Map<ToolSpecification, ToolExecutor>) tools);
            } else if (tools.getClass().isArray()) {
                agentBuilder.tools((Object[]) tools);
            } else {
                agentBuilder.tools(tools);
            }
        });

        getAnnotatedMethodOnClass(agentType, ToolProviderSupplier.class).ifPresent(method -> {
            checkReturnType(method, ToolProvider.class);
            agentBuilder.toolProvider(invokeSupplierWithResolvers(agentType, method, ToolProvider.class));
        });

        getAnnotatedMethodOnClass(agentType, ToolSearchStrategySupplier.class).ifPresent(method -> {
            checkReturnType(method, ToolSearchStrategy.class);
            agentBuilder.toolSearchStrategy(invokeSupplierWithResolvers(agentType, method, ToolSearchStrategy.class));
        });

        getAnnotatedMethodOnClass(agentType, ContentRetrieverSupplier.class).ifPresent(method -> {
            checkReturnType(method, ContentRetriever.class);
            agentBuilder.contentRetriever(invokeSupplierWithResolvers(agentType, method, ContentRetriever.class));
        });

        getAnnotatedMethodOnClass(agentType, RetrievalAugmentorSupplier.class).ifPresent(method -> {
            checkReturnType(method, RetrievalAugmentor.class);
            agentBuilder.retrievalAugmentor(invokeSupplierWithResolvers(agentType, method, RetrievalAugmentor.class));
        });

        getAnnotatedMethodOnClass(agentType, ChatMemoryProviderSupplier.class).ifPresent(method -> {
            checkReturnType(method, ChatMemory.class);
            ResolvedParameters parameters =
                    ResolvedParameters.of(agentType, method).checkFrameworkParameters(Object.class);
            agentBuilder.chatMemoryProvider(memoryId -> parameters.invoke(memoryId));
        });

        getAnnotatedMethodOnClass(agentType, ChatMemorySupplier.class).ifPresent(method -> {
            checkReturnType(method, ChatMemory.class);
            agentBuilder.chatMemory(invokeSupplierWithResolvers(agentType, method, ChatMemory.class));
        });

        getAnnotatedMethodOnClass(agentType, ChatModelSupplier.class)
                .ifPresentOrElse(
                        method -> {
                            checkReturnType(method, ChatModel.class);
                            if (method.getParameterCount() > 0) {
                                agentBuilder.chatModel(agenticScopeFunction(agentType, method, ChatModel.class));
                            } else {
                                agentBuilder.chatModel((ChatModel) invokeStatic(method));
                            }
                        },
                        () -> getAnnotatedMethodOnClass(agentType, StreamingChatModelSupplier.class)
                                .ifPresentOrElse(
                                        method -> {
                                            checkReturnType(method, StreamingChatModel.class);
                                            if (method.getParameterCount() > 0) {
                                                agentBuilder.streamingChatModel(agenticScopeFunction(
                                                        agentType, method, StreamingChatModel.class));
                                            } else {
                                                agentBuilder.streamingChatModel(
                                                        (StreamingChatModel) invokeStatic(method));
                                            }
                                        },
                                        () -> {
                                            if (chatModel == null && !allowNullChatModel) {
                                                throw new IllegalArgumentException(
                                                        "ChatModel not provided for subagent " + agentType.getName()
                                                                + ". Please provide one either with a static method annotated with @ChatModelSupplier "
                                                                + "or @StreamingChatModelSupplier, or through the parent agent's chatModel parameter.");
                                            }
                                            agentBuilder.chatModel(chatModel);
                                        }));

        getAnnotatedMethodOnClass(agentType, AgentListenerSupplier.class).ifPresent(listenerMethod -> {
            checkReturnType(listenerMethod, AgentListener.class);
            agentBuilder.listener(invokeSupplierWithResolvers(agentType, listenerMethod, AgentListener.class));
        });

        getAnnotatedMethodOnClass(agentType, SystemMessageProviderSupplier.class)
                .ifPresent(method -> {
                    checkReturnType(method, String.class);
                    ResolvedParameters parameters =
                            ResolvedParameters.of(agentType, method).checkFrameworkParameters(Object.class);
                    agentBuilder.systemMessageProvider(memoryId -> parameters.invoke(memoryId));
                });

        getAnnotatedMethodOnClass(agentType, UserMessageProviderSupplier.class).ifPresent(method -> {
            checkReturnType(method, String.class);
            ResolvedParameters parameters =
                    ResolvedParameters.of(agentType, method).checkFrameworkParameters(Object.class);
            agentBuilder.userMessageProvider(memoryId -> parameters.invoke(memoryId));
        });

        AgentCondition activationCondition = activationCondition(agentType);
        if (activationCondition != null) {
            agentBuilder.activationCondition(activationCondition.description(), activationCondition.predicate());
        }

        if (agentConfigurator.agentInstanceFactory() != null) {
            agentBuilder.agentInstanceFactory(agentConfigurator.agentInstanceFactory());
        }

        agentConfigurator.configurator().accept(new DefaultDeclarativeAgentCreationContext(agentType, agentBuilder));
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
        configureDecisionAgent(agentType, builder, AgenticServices.AgentConfigurator.empty());
    }

    /**
     * Applies the declarative configuration of the given decision agent interface to its builder: the
     * {@link DecisionModelSupplier}, the {@link DecisionThresholdSupplier}, the {@link AgentListenerSupplier}, the
     * {@link ActivationCondition} and the agent configurator.
     *
     * @throws AgenticSystemConfigurationException if the interface also declares a {@link ChatModelSupplier} or a
     * {@link StreamingChatModelSupplier}
     */
    public static void configureDecisionAgent(
            Class<?> agentType, DecisionAgentBuilder<?> builder, AgenticServices.AgentConfigurator agentConfigurator) {
        for (Class<? extends Annotation> chatModelAnnotation :
                List.of(ChatModelSupplier.class, StreamingChatModelSupplier.class)) {
            if (getAnnotatedMethodOnClass(agentType, chatModelAnnotation).isPresent()) {
                throw new AgenticSystemConfigurationException(
                        "The decision agent " + agentType.getName()
                                + " declares a method annotated with @" + chatModelAnnotation.getSimpleName()
                                + ": a decision agent uses a decision model, provided with @DecisionModelSupplier, and no chat model");
            }
        }

        getAnnotatedMethodOnClass(agentType, DecisionModelSupplier.class).ifPresent(method -> {
            checkReturnType(method, DecisionModel.class);
            if (frameworkParameterCount(agentType, method) > 0) {
                builder.decisionModelProvider(agenticScopeFunction(agentType, method, DecisionModel.class));
            } else {
                builder.decisionModel(invokeSupplierWithResolvers(agentType, method, DecisionModel.class));
            }
        });

        getAnnotatedMethodOnClass(agentType, DecisionThresholdSupplier.class).ifPresent(method -> {
            if (method.getReturnType() != double.class && method.getReturnType() != Double.class) {
                throw new IllegalArgumentException("Method " + method + " must return double");
            }
            ResolvedParameters threshold =
                    ResolvedParameters.of(agentType, method).checkFrameworkParameters(ThresholdContext.class);
            builder.thresholdProvider(context -> threshold.invoke(context));
        });

        getAnnotatedMethodOnClass(agentType, AgentListenerSupplier.class).ifPresent(method -> {
            checkReturnType(method, AgentListener.class);
            builder.listener(invokeSupplierWithResolvers(agentType, method, AgentListener.class));
        });

        AgentCondition activationCondition = activationCondition(agentType);
        if (activationCondition != null) {
            builder.activationCondition(activationCondition.description(), activationCondition.predicate());
        }

        if (agentConfigurator.agentInstanceFactory() != null) {
            builder.agentInstanceFactory(agentConfigurator.agentInstanceFactory());
        }

        agentConfigurator.configurator().accept(new DefaultDeclarativeDecisionAgentCreationContext(agentType, builder));
    }

    /**
     * Checks that the given type, built as an agent of another kind, declares no method annotated with
     * {@link DecisionModelSupplier} or {@link DecisionThresholdSupplier}, which would be silently ignored.
     *
     * @throws AgenticSystemConfigurationException if it does
     */
    public static void checkNoDecisionSuppliers(Class<?> agentType) {
        for (Class<? extends Annotation> decisionAnnotation :
                List.of(DecisionModelSupplier.class, DecisionThresholdSupplier.class)) {
            if (getAnnotatedMethodOnClass(agentType, decisionAnnotation).isPresent()) {
                throw new AgenticSystemConfigurationException("The agent " + agentType.getName()
                        + " declares a method annotated with @" + decisionAnnotation.getSimpleName()
                        + ", but it is not built as a decision agent: a decision agent is an interface with an @Agent method "
                        + "annotated with @Decide, or declaring a @DecisionModelSupplier, built with "
                        + "AgenticServices.decisionAgentBuilder or by the declarative API");
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
        selectMethod(agentServiceClass, method -> method.isAnnotationPresent(Output.class))
                .map(m -> agenticScopeFunction(agentServiceClass, m, Object.class))
                .ifPresent(builder::output);
    }

    /**
     * Selects the static condition method matching the given selector.
     *
     * @throws IllegalArgumentException if the selected method doesn't return a boolean
     */
    public static Optional<Method> predicateMethod(Class<?> agentServiceClass, Predicate<Method> methodSelector) {
        Optional<Method> method = selectMethod(agentServiceClass, methodSelector);
        method.ifPresent(m -> {
            if (m.getReturnType() != boolean.class && m.getReturnType() != Boolean.class) {
                throw new IllegalArgumentException("Method " + m + " must return boolean");
            }
        });
        return method;
    }

    public static void buildAgentFeatures(Class<?> agentServiceClass, AgenticService<?, ?> builder) {
        checkNoDecisionSuppliers(agentServiceClass);
        buildBeforeCall(agentServiceClass).ifPresent(builder::beforeCall);
        buildErrorHandler(agentServiceClass).ifPresent(builder::errorHandler);
        buildListener(agentServiceClass, builder);
        AgentCondition activationCondition = activationCondition(agentServiceClass);
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
        boolean conditionalAgent =
                getAnnotatedMethodOnClass(agentClass, ConditionalAgent.class).isPresent();
        Method conditionMethod = null;
        for (Method method : staticMethodsAnnotatedWith(agentClass, ActivationCondition.class)) {
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
                        + "allowed in " + agentClass.getName() + ", but found " + conditionMethod.getName() + " and "
                        + method.getName());
            }
            conditionMethod = method;
        }
        return conditionMethod == null
                ? null
                : new AgentCondition(
                        conditionMethod.getAnnotation(ActivationCondition.class).description(),
                        agenticScopeFunction(agentClass, conditionMethod, Boolean.class, false)::apply);
    }

    private static Set<Method> staticMethodsAnnotatedWith(Class<?> type, Class<? extends Annotation> annotation) {
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

    private static Optional<Consumer<AgenticScope>> buildBeforeCall(Class<?> agentServiceClass) {
        return selectMethod(agentServiceClass, method -> method.isAnnotationPresent(BeforeCall.class))
                .map(m -> {
                    checkReturnType(m, void.class);
                    return agenticScopeFunction(agentServiceClass, m, Object.class)::apply;
                });
    }

    public static Optional<Executor> parallelExecutor(Class<?> agentServiceClass) {
        return selectMethod(agentServiceClass, method -> method.isAnnotationPresent(ParallelExecutor.class))
                .map(method -> {
                    checkReturnType(method, Executor.class);
                    return invokeSupplierWithResolvers(agentServiceClass, method, Executor.class);
                });
    }

    private static <T> Optional<Function<ErrorContext, ErrorRecoveryResult>> buildErrorHandler(
            Class<T> agentServiceClass) {
        return selectMethod(agentServiceClass, method -> method.isAnnotationPresent(ErrorHandler.class))
                .map(m -> {
                    checkReturnType(m, ErrorRecoveryResult.class);
                    ResolvedParameters parameters =
                            ResolvedParameters.of(agentServiceClass, m).checkFrameworkParameters(ErrorContext.class);
                    return errorContext -> parameters.invoke(errorContext);
                });
    }

    private static void buildListener(Class<?> agentServiceClass, AgenticService<?, ?> builder) {
        getAnnotatedMethodOnClass(agentServiceClass, AgentListenerSupplier.class)
                .ifPresent(listenerMethod -> {
                    checkReturnType(listenerMethod, AgentListener.class);
                    builder.listener(
                            invokeSupplierWithResolvers(agentServiceClass, listenerMethod, AgentListener.class));
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
        if (method.getParameterCount() == 0) {
            return invokeStatic(method);
        }
        return ResolvedParameters.of(agentType, method).checkAllResolved().invoke();
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
        return agenticScopeFunction(agentType, functionMethod, targetClass, true);
    }

    private static <T> Function<AgenticScope, T> agenticScopeFunction(
            Class<?> agentType, Method functionMethod, Class<T> targetClass, boolean writeConvertedArguments) {
        ResolvedParameters parameters = ResolvedParameters.of(agentType, functionMethod);
        return agenticScope -> {
            try {
                Object[] args = parameters
                        .invocationArguments(agenticScope, Map.of(), writeConvertedArguments)
                        .positionalArgs();
                return (T) functionMethod.invoke(null, args);
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
