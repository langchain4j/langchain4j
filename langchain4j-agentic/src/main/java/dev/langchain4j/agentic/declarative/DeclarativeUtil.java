package dev.langchain4j.agentic.declarative;

import static dev.langchain4j.agentic.internal.AgentUtil.AGENTIC_SCOPE_ARG_NAME;
import static dev.langchain4j.agentic.internal.AgentUtil.agentInvocationArguments;
import static dev.langchain4j.agentic.internal.AgentUtil.argumentFromParameter;
import static dev.langchain4j.agentic.internal.AgentUtil.argumentsFromMethod;
import static dev.langchain4j.agentic.internal.AgentUtil.getAnnotatedMethodOnClass;

import dev.langchain4j.Internal;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.AgenticServices.DefaultDeclarativeAgentCreationContext;
import dev.langchain4j.agentic.agent.AgentBuilder;
import dev.langchain4j.agentic.agent.ErrorContext;
import dev.langchain4j.agentic.agent.ErrorRecoveryResult;
import dev.langchain4j.agentic.observability.AgentListener;
import dev.langchain4j.agentic.planner.AgentArgument;
import dev.langchain4j.agentic.planner.AgenticService;
import dev.langchain4j.agentic.planner.AgenticSystemConfigurationException;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.service.tool.ToolExecutor;
import dev.langchain4j.service.tool.ToolProvider;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
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
        getAnnotatedMethodOnClass(agentType, ToolsSupplier.class).ifPresent(method -> {
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
            if (method.getParameterCount() == 0) {
                throw new IllegalArgumentException(
                        "Method " + method + " must have at least 1 argument: [class java.lang.Object]");
            }
            if (method.getParameterCount() == 1) {
                checkArguments(method, Object.class);
                agentBuilder.chatMemoryProvider(memoryId -> invokeStatic(method, memoryId));
            } else {
                agentBuilder.chatMemoryProvider(memoryId -> {
                    Function<AgenticScope, ChatMemory> fn =
                            agenticScopeFunctionWithSupplierParameterResolver(agentType, method, ChatMemory.class);
                    return fn.apply(null);
                });
            }
        });

        getAnnotatedMethodOnClass(agentType, ChatMemorySupplier.class).ifPresent(method -> {
            checkReturnType(method, ChatMemory.class);
            agentBuilder.chatMemory(invokeSupplierWithResolvers(agentType, method, ChatMemory.class));
        });

        getAnnotatedMethodOnClass(agentType, ChatModelSupplier.class)
                .ifPresentOrElse(
                        method -> {
                            if (method.getParameterCount() > 0) {
                                Function<AgenticScope, ChatModel> scopeFunction =
                                        agenticScopeFunctionWithSupplierParameterResolver(
                                                agentType, method, ChatModel.class);
                                Function<AgenticScope, ChatModel> provider = scope -> {
                                    if (scope == null) {
                                        return invokeStatic(method, new Object[method.getParameterCount()]);
                                    }
                                    return scopeFunction.apply(scope);
                                };
                                agentBuilder.chatModel(provider);
                            } else {
                                agentBuilder.chatModel((ChatModel) invokeStatic(method));
                            }
                        },
                        () -> getAnnotatedMethodOnClass(agentType, StreamingChatModelSupplier.class)
                                .ifPresentOrElse(
                                        method -> {
                                            if (method.getParameterCount() > 0) {
                                                Function<AgenticScope, StreamingChatModel> scopeFunction =
                                                        agenticScopeFunctionWithSupplierParameterResolver(
                                                                agentType, method, StreamingChatModel.class);
                                                Function<AgenticScope, StreamingChatModel> provider = scope -> {
                                                    if (scope == null) {
                                                        return invokeStatic(
                                                                method, new Object[method.getParameterCount()]);
                                                    }
                                                    return scopeFunction.apply(scope);
                                                };
                                                agentBuilder.streamingChatModel(provider);
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

        getAnnotatedMethodOnClass(agentType, SystemMessageProviderSupplier.class).ifPresent(method -> {
            checkReturnType(method, String.class);
            checkArguments(method, Object.class);
            agentBuilder.systemMessageProvider(memoryId -> invokeStatic(method, memoryId));
        });

        getAnnotatedMethodOnClass(agentType, UserMessageProviderSupplier.class).ifPresent(method -> {
            checkReturnType(method, String.class);
            checkArguments(method, Object.class);
            agentBuilder.userMessageProvider(memoryId -> invokeStatic(method, memoryId));
        });

        if (agentConfigurator.agentInstanceFactory() != null) {
            agentBuilder.agentInstanceFactory(agentConfigurator.agentInstanceFactory());
        }

        agentConfigurator.configurator().accept(new DefaultDeclarativeAgentCreationContext(agentType, agentBuilder));
    }

    public static void checkArguments(Method method, Class<?>... expected) {
        Class<?>[] actual = method.getParameterTypes();
        if (actual.length != expected.length) {
            throw new IllegalArgumentException(
                    "Method " + method + " must have " + expected.length + " arguments: " + Arrays.toString(expected));
        }
        for (int i = 0; i < expected.length; i++) {
            if (!expected[i].isAssignableFrom(actual[i])) {
                throw new IllegalArgumentException(
                        "Method " + method + " argument " + (i + 1) + " must be of type " + expected[i].getName());
            }
        }
    }

    public static void checkReturnType(Method method, Class<?> expected) {
        if (!method.getReturnType().isAssignableFrom(expected)) {
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
                .map(m -> agenticScopeFunction(m, Object.class))
                .ifPresent(builder::output);
    }

    public static Optional<Method> predicateMethod(Class<?> agentServiceClass, Predicate<Method> methodSelector) {
        return selectMethod(
                agentServiceClass,
                methodSelector.and(m -> (m.getReturnType() == boolean.class || m.getReturnType() == Boolean.class)));
    }

    public static void buildAgentFeatures(Class<?> agentServiceClass, AgenticService<?, ?> builder) {
        buildErrorHandler(agentServiceClass).ifPresent(builder::errorHandler);
        buildListener(agentServiceClass, builder);
    }

    public static Optional<Executor> parallelExecutor(Class<?> agentServiceClass) {
        return selectMethod(
                        agentServiceClass,
                        method -> method.isAnnotationPresent(ParallelExecutor.class)
                                && Executor.class.isAssignableFrom(method.getReturnType()))
                .map(method -> invokeParallelExecutor(agentServiceClass, method));
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
        return agenticScope ->
                agenticScopeFunction(predicateMethod, boolean.class).apply(agenticScope);
    }

    private static <T> T invokeSupplierWithResolvers(Class<?> agentType, Method method, Class<T> targetClass) {
        if (method.getParameterCount() == 0) {
            return invokeStatic(method);
        }
        List<SupplierParameterResolver> resolvers = getSupplierParameterResolvers();
        if (resolvers.isEmpty()) {
            return invokeStatic(method, new Object[method.getParameterCount()]);
        }
        Function<AgenticScope, T> fn =
                agenticScopeFunctionWithSupplierParameterResolver(agentType, method, targetClass);
        return fn.apply(null);
    }

    private static <T> Function<AgenticScope, T> agenticScopeFunctionWithSupplierParameterResolver(
            Class<?> agentType, Method functionMethod, Class<T> targetClass) {
        List<SupplierParameterResolver> resolvers = getSupplierParameterResolvers();
        if (resolvers.isEmpty()) {
            return agenticScopeFunction(functionMethod, targetClass);
        }

        Parameter[] parameters = functionMethod.getParameters();
        List<AgentArgument> unresolvedAgentArguments = new ArrayList<>(parameters.length);
        List<Integer> unresolvedParameterIndexes = new ArrayList<>(parameters.length);

        SupplierParameterResolver.Context[] contexts = new SupplierParameterResolver.Context[parameters.length];
        SupplierParameterResolver[] paramResolvers = new SupplierParameterResolver[parameters.length];
        for (int i = 0; i < parameters.length; i++) {
            SupplierParameterResolver.Context ctx =
                    new DefaultSupplierParameterResolverContext(agentType, functionMethod, parameters[i]);
            for (SupplierParameterResolver resolver : resolvers) {
                if (resolver.supports(ctx)) {
                    contexts[i] = ctx;
                    paramResolvers[i] = resolver;
                    break;
                }
            }
            if (paramResolvers[i] == null) {
                unresolvedAgentArguments.add(argumentFromParameter(parameters[i]));
                unresolvedParameterIndexes.add(i);
            }
        }

        return agenticScope -> {
            try {
                Object[] args = new Object[parameters.length];
                for (int i = 0; i < paramResolvers.length; i++) {
                    if (paramResolvers[i] != null) {
                        args[i] = paramResolvers[i].resolve(contexts[i]);
                    }
                }
                Map<String, Object> additionalArgs = new HashMap<>();
                additionalArgs.put(AGENTIC_SCOPE_ARG_NAME, agenticScope);
                Object[] unresolvedArgs = agentInvocationArguments(
                                agenticScope, unresolvedAgentArguments, additionalArgs)
                        .positionalArgs();
                for (int i = 0; i < unresolvedArgs.length; i++) {
                    args[unresolvedParameterIndexes.get(i)] = unresolvedArgs[i];
                }
                return (T) functionMethod.invoke(null, args);
            } catch (Exception e) {
                throw new RuntimeException("Error invoking method: " + functionMethod.getName(), e);
            }
        };
    }

    private static Executor invokeParallelExecutor(Class<?> agentType, Method method) {
        if (method.getParameterCount() == 0) {
            return invokeStatic(method);
        }
        List<SupplierParameterResolver> resolvers = getSupplierParameterResolvers();
        if (resolvers.isEmpty()) {
            throw missingSupplierParameterResolver(method, method.getParameters()[0]);
        }

        Parameter[] parameters = method.getParameters();
        Object[] args = new Object[parameters.length];
        for (int i = 0; i < parameters.length; i++) {
            SupplierParameterResolver.Context ctx =
                    new DefaultSupplierParameterResolverContext(agentType, method, parameters[i]);
            SupplierParameterResolver resolver = null;
            for (SupplierParameterResolver candidate : resolvers) {
                if (candidate.supports(ctx)) {
                    resolver = candidate;
                    break;
                }
            }
            if (resolver == null) {
                throw missingSupplierParameterResolver(method, parameters[i]);
            }
            args[i] = resolver.resolve(ctx);
        }
        return invokeStatic(method, args);
    }

    private static AgenticSystemConfigurationException missingSupplierParameterResolver(
            Method method, Parameter parameter) {
        return new AgenticSystemConfigurationException("No SupplierParameterResolver is registered for parameter "
                + parameter + " of @ParallelExecutor method " + method + ".");
    }

    public static <T> Function<AgenticScope, T> agenticScopeFunction(Method functionMethod, Class<T> targetClass) {
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

    public static void addSupplierParameterResolver(SupplierParameterResolver resolver) {
        supplierParameterResolvers.add(resolver);
    }

    public static List<SupplierParameterResolver> getSupplierParameterResolvers() {
        return supplierParameterResolvers;
    }

    private record DefaultSupplierParameterResolverContext(
            Class<?> declaringAgentClass, Method supplierMethod, Parameter parameter)
            implements SupplierParameterResolver.Context {}
}
