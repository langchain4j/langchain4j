package dev.langchain4j.agentic;

import dev.langchain4j.agentic.cognisphere.Cognisphere;
import dev.langchain4j.agentic.internal.AgentInstance;
import dev.langchain4j.agentic.internal.Context;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServiceContext;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.memory.ChatMemoryAccess;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.function.Function;

public class AgentBuilder<T> {

    private final Class<T> agentServiceClass;
    private final AiServices<T> aiServices;
    private final AiServiceContext context;

    private String outputName;

    AgentBuilder(Class<T> agentServiceClass, Method agenticMethod) {
        this.agentServiceClass = agentServiceClass;
        this.context = new AiServiceContext(agentServiceClass);
        this.aiServices = AiServices.builder(context);

        Agent agent = agenticMethod.getAnnotation(Agent.class);
        if (agent == null) {
            throw new IllegalArgumentException("Method " + agenticMethod + " is not annotated with @Agent");
        }
        if (!agent.outputName().isEmpty()) {
            outputName = agent.outputName();
        }
    }

    public T build() {
        T delegate = aiServices.build();

        Object agent = Proxy.newProxyInstance(
                agentServiceClass.getClassLoader(),
                new Class<?>[] {agentServiceClass, AgentInstance.class, ChatMemoryAccess.class},
                new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) throws Exception {
                        if (method.getDeclaringClass() == ChatMemoryAccess.class) {
                            return switch (method.getName()) {
                                case "getChatMemory" -> context.hasChatMemory() ? context.chatMemoryService.getChatMemory(args[0]) : null;
                                case "evictChatMemory" -> context.hasChatMemory() && context.chatMemoryService.evictChatMemory(args[0]) != null;
                                default ->
                                        throw new UnsupportedOperationException(
                                                "Unknown method on ChatMemoryAccess class : " + method.getName());
                            };
                        }

                        if (method.getDeclaringClass() == AgentInstance.class) {
                            return switch (method.getName()) {
                                case "outputName" -> outputName;
                                default ->
                                        throw new UnsupportedOperationException(
                                                "Unknown method on ChatMemoryAccess class : " + method.getName());
                            };
                        }

                        return method.invoke(delegate, args);
                    }
                });

        return (T) agent;
    }

    public AgentBuilder<T> chatModel(ChatModel model) {
        aiServices.chatModel(model);
        return this;
    }

    public AgentBuilder<T> chatMemory(ChatMemory chatMemory) {
        aiServices.chatMemory(chatMemory);
        return this;
    }

    public AgentBuilder<T> chatMemoryProvider(ChatMemoryProvider chatMemoryProvider) {
        aiServices.chatMemoryProvider(chatMemoryProvider);
        return this;
    }

    public AgentBuilder<T> tools(Object... objectsWithTools) {
        aiServices.tools(objectsWithTools);
        return this;
    }

    public AgentBuilder<T> outputName(String outputName) {
        this.outputName = outputName;
        return this;
    }

    public AgentBuilder<T> context(Function<Cognisphere, String> contextProvider) {
        aiServices.chatRequestTransformer(new Context.CognisphereContextGenerator(contextProvider));
        return this;
    }

    public AgentBuilder<T> summarizedContext(String... agentNames) {
        if (context.chatModel == null) {
            throw new IllegalStateException("ChatModel must be set before using summarizedContext");
        }
        aiServices.chatRequestTransformer(new Context.Summarizer(context.chatModel, agentNames));
        return this;
    }
}
