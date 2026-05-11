package dev.langchain4j.agentic.declarative;

import dev.langchain4j.Internal;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agentic.AgenticServices.DeclarativeAgentCreationContext;
import dev.langchain4j.agentic.AgenticServices.DefaultDeclarativeAgentCreationContext;
import dev.langchain4j.agentic.agent.AgentBuilder;
import dev.langchain4j.agentic.agent.ErrorContext;
import dev.langchain4j.agentic.agent.ErrorRecoveryResult;
import dev.langchain4j.agentic.observability.AgentListener;
import dev.langchain4j.agentic.planner.AgentArgument;
import dev.langchain4j.agentic.planner.AgenticService;
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
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import static dev.langchain4j.agentic.internal.AgentUtil.AGENTIC_SCOPE_ARG_NAME;
import static dev.langchain4j.agentic.internal.AgentUtil.agentInvocationArguments;
import static dev.langchain4j.agentic.internal.AgentUtil.argumentsFromMethod;
import static dev.langchain4j.agentic.internal.AgentUtil.getAnnotatedMethodOnClass;

@Internal
public class DeclarativeUtil {

    private DeclarativeUtil() { }

    public static void configureAgent(Class<?> agentType, AgentBuilder<?, ?> agentBuilder) {
        configureAgent(agentType, null, true, agentBuilder, ctx -> { });
    }

    public static void configureAgent(Class<?> agentType, ChatModel chatModel, AgentBuilder<?, ?> agentBuilder, Consumer<DeclarativeAgentCreationContext<?>> agentConfigurator) {
        configureAgent(agentType, chatModel, false, agentBuilder, agentConfigurator);
    }

    private static void configureAgent(Class<?> agentType, ChatModel chatModel, boolean allowNullChatModel, AgentBuilder<?, ?> agentBuilder, Consumer<DeclarativeAgentCreationContext<?>> agentConfigurator) {
        getAnnotatedMethodOnClass(agentType, ToolsSupplier.class)
                .ifPresent(method -> {
                    checkArguments(method);
                    Object tools = invokeStatic(method);
                    if (tools instanceof Map) {
                        agentBuilder.tools((Map<ToolSpecification, ToolExecutor>) tools);
                    } else if (tools.getClass().isArray()) {
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
                            if (method.getParameterCount() > 0) {
                                Function<AgenticScope, ChatModel> scopeFunction = agenticScopeFunction(method, ChatModel.class);
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
                                .ifPresentOrElse(method -> {
                                            if (method.getParameterCount() > 0) {
                                                Function<AgenticScope, StreamingChatModel> scopeFunction = agenticScopeFunction(method, StreamingChatModel.class);
                                                Function<AgenticScope, StreamingChatModel> provider = scope -> {
                                                    if (scope == null) {
                                                        return invokeStatic(method, new Object[method.getParameterCount()]);
                                                    }
                                                    return scopeFunction.apply(scope);
                                                };
                                                agentBuilder.streamingChatModel(provider);
                                            } else {
                                                agentBuilder.streamingChatModel((StreamingChatModel) invokeStatic(method));
                                            }
                                        },
                                        () -> {
                                            if (chatModel == null && !allowNullChatModel) {
                                                throw new IllegalArgumentException("ChatModel not provided for subagent " + agentType.getName() +
                                                        ". Please provide one either with a static method annotated with @ChatModelSupplier " +
                                                        "or @StreamingChatModelSupplier, or through the parent agent's chatModel parameter.");
                                            }
                                            agentBuilder.chatModel(chatModel);
                                        }));

        getAnnotatedMethodOnClass(agentType, AgentListenerSupplier.class)
                .ifPresent(listenerMethod -> {
                    checkReturnType(listenerMethod, AgentListener.class);
                    agentBuilder.listener(invokeStatic(listenerMethod));
                });

        agentConfigurator.accept(new DefaultDeclarativeAgentCreationContext(agentType, agentBuilder));
    }

    public static void checkArguments(Method method, Class<?>... expected) {
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
}
