package dev.langchain4j.agentic.declarative;

import dev.langchain4j.Internal;
import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.agent.AgentBuilder;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.service.tool.ToolProvider;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.function.Consumer;

import static dev.langchain4j.agentic.internal.AgentUtil.getAnnotatedMethodOnClass;

@Internal
public class DeclarativeUtil {

    public static void configureAgent(Class<?> agentType, AgentBuilder<?> agentBuilder) {
        configureAgent(agentType, null, true, agentBuilder, ctx -> { });
    }

    public static void configureAgent(Class<?> agentType, ChatModel chatModel, AgentBuilder<?> agentBuilder, Consumer<AgenticServices.DeclarativeAgentCreationContext> agentConfigurator) {
        configureAgent(agentType, chatModel, false, agentBuilder, ctx -> { });
    }

    private static void configureAgent(Class<?> agentType, ChatModel chatModel, boolean allowNullChatModel, AgentBuilder<?> agentBuilder, Consumer<AgenticServices.DeclarativeAgentCreationContext> agentConfigurator) {
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
                            if (chatModel == null && !allowNullChatModel) {
                                throw new IllegalArgumentException("ChatModel not provided for subagent " + agentType.getName() +
                                        ". Please provide a ChatModel either through the @ChatModelSupplier annotation on a static method " +
                                        "or through the parent agent's chatModel parameter.");
                            }
                            agentBuilder.chatModel(chatModel);
                        });

        agentConfigurator.accept(new AgenticServices.DefaultDeclarativeAgentCreationContext(agentType, agentBuilder));
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

    public static <T> T invokeStatic(Method method, Object... args) {
        try {
            return (T) method.invoke(null, args);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }
}
