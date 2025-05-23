package dev.langchain4j.agentic;

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
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import static dev.langchain4j.internal.Exceptions.illegalArgument;
import static dev.langchain4j.internal.Utils.getAnnotatedMethod;

public class AgentServices<T> {

    private final Class<T> agentServiceClass;
    private final AiServices<T> aiServices;
    private final AiServiceContext context;

    private final Map<String, AgentSpecification> agentsSpecs = new HashMap<>();
    private final Map<String, Object> agents = new HashMap<>();

    private Function<AgentRequest, AgentDirective> onRequest = AgentRequest.DEFAULT_ACTION;
    private Function<AgentResponse, AgentDirective> onResponse = AgentResponse.DEFAULT_ACTION;

    private AgentServices(Class<T> agentServiceClass) {
        this.agentServiceClass = agentServiceClass;
        this.context = new AiServiceContext(agentServiceClass);
        this.aiServices = AiServices.builder(context);
    }

    public static <T> AgentServices<T> builder(Class<T> agentServiceClass) {
        return new AgentServices<>(agentServiceClass);
    }

    public T build() {
        T delegate = aiServices.build();

        Object agent = Proxy.newProxyInstance(
                agentServiceClass.getClassLoader(),
                new Class<?>[] {agentServiceClass, ChatMemoryInjectable.class},
                new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) throws Exception {
                        ChatState chatState = chatState(method, args);
                        if (method.getDeclaringClass() == ChatMemoryInjectable.class) {
                            if (chatState != null) {
                                throw illegalArgument("Memory already present, cannot inject a different one");
                            }
                            return switch (method.getName()) {
                                case "setChatMemory" -> aiServices.chatMemory((ChatMemory) args[0]);
                                default ->
                                        throw new UnsupportedOperationException(
                                                "Unknown method on ChatMemoryAccess class : " + method.getName());
                            };
                        }

                        while (true) {
                            AgentDirective requestDirective = onRequest.apply(new AgentRequest(method.getName(), chatState));
                            if (requestDirective instanceof AgentDirective.Redirect redirect) {
                                Object agent = agents.get(redirect.agentName());
                                if (agent == null) {
                                    throw illegalArgument("No agent found with name '%s'", redirect.agentName());
                                }
                                return agentsSpecs.get(redirect.agentName()).method().invoke(agent, args);
                            }

                            Object response = method.invoke(delegate, args);
                            AgentDirective responseDirective = onResponse.apply(new AgentResponse(method.getName(), chatState, response));
                            if (responseDirective instanceof AgentDirective.Terminate) {
                                return response;
                            }
                        }
                    }
                });

        return (T) agent;
    }

    public AgentServices<T> chatModel(ChatModel model) {
        aiServices.chatModel(model);
        return this;
    }

    public AgentServices<T> chatState(ChatState chatState) {
        aiServices.chatMemory(chatState);
        return this;
    }

    public AgentServices<T> tools(Object... objectsWithTools) {
        aiServices.tools(objectsWithTools);
        return this;
    }

    public AgentServices<T> agents(Object... agents) {
        for (Object agent : agents) {
            for (Method method : agent.getClass().getDeclaredMethods()) {
                getAnnotatedMethod(method, Agent.class).ifPresent(agentMethod -> processAgentMethod(agent, agentMethod) );
            }
        }
        return this;
    }

    private void processAgentMethod(Object agent, Method agentMethod) {
        AgentSpecification agentSpecification = AgentSpecification.fromMethod(agentMethod);
        agentsSpecs.put(agentSpecification.name(), agentSpecification);
        agents.put(agentSpecification.name(), agent);
    }

    public AgentServices<T> onRequest(Function<AgentRequest, AgentDirective> onRequest) {
        this.onRequest = onRequest;
        return this;
    }

    public AgentServices<T> onResponse(Function<AgentResponse, AgentDirective> onResponse) {
        this.onResponse = onResponse;
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
