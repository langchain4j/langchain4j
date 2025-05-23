package dev.langchain4j.agentic;

import dev.langchain4j.agentic.internal.AgentInstance;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServiceContext;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.memory.ChatMemoryService;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.Proxy;
import java.util.Optional;

import static dev.langchain4j.internal.Exceptions.illegalArgument;

public class AgentBuilder<T> {

    private final Class<T> agentServiceClass;
    private final AiServices<T> aiServices;
    private final AiServiceContext context;

    private String outputName;

    AgentBuilder(Class<T> agentServiceClass) {
        this.agentServiceClass = agentServiceClass;
        this.context = new AiServiceContext(agentServiceClass);
        this.aiServices = AiServices.builder(context);
    }

    public T build() {
        T delegate = aiServices.build();

        Object agent = Proxy.newProxyInstance(
                agentServiceClass.getClassLoader(),
                new Class<?>[] {agentServiceClass, AgentInstance.class},
                new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) throws Exception {
                        if (method.getDeclaringClass() == AgentInstance.class) {
                            return switch (method.getName()) {
                                case "trySetChatMemory" -> {
                                    aiServices.chatMemory((ChatMemory) args[0]);
                                    yield true;
                                }
                                case "outputName" -> outputName;
                                default ->
                                        throw new UnsupportedOperationException(
                                                "Unknown method on ChatMemoryAccess class : " + method.getName());
                            };
                        }

                        Object response = method.invoke(delegate, args);

                        ChatState chatState = chatState(method, args);
                        if (chatState != null && outputName != null) {
                            chatState.writeState(outputName, response);
                        }
                        return response;
                    }
                });

        return (T) agent;
    }

    public AgentBuilder<T> chatModel(ChatModel model) {
        aiServices.chatModel(model);
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

    private ChatState chatState(Method method, Object[] args) {
        Object memoryId = findMemoryId(method, args).orElse(ChatMemoryService.DEFAULT);
        ChatMemory chatMemory = context.hasChatMemory()
                ? context.chatMemoryService.getOrCreateChatMemory(memoryId)
                : null;
        return chatMemory instanceof ChatState chatState ? chatState : null;
    }

    private static Optional<Object> findMemoryId(Method method, Object[] args) {
        Parameter[] parameters = method.getParameters();
        for (int i = 0; i < parameters.length; i++) {
            if (parameters[i].isAnnotationPresent(MemoryId.class)) {
                Object memoryId = args[i];
                if (memoryId == null) {
                    throw illegalArgument(
                            "The value of parameter '%s' annotated with @MemoryId in method '%s' must not be null",
                            parameters[i].getName(), method.getName());
                }
                return Optional.of(memoryId);
            }
        }
        return Optional.empty();
    }

}
