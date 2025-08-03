package dev.langchain4j.agentic.agent;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.cognisphere.Cognisphere;
import dev.langchain4j.agentic.cognisphere.DefaultCognisphere;
import dev.langchain4j.agentic.internal.AgentInstance;
import dev.langchain4j.agentic.internal.CognisphereOwner;
import dev.langchain4j.agentic.internal.Context;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServiceContext;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.memory.ChatMemoryAccess;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.function.Function;

public class AgentBuilder<T> {

    private final Class<T> agentServiceClass;

    String outputName;

    private ChatModel model;
    private ChatMemory chatMemory;
    private ChatMemoryProvider chatMemoryProvider;
    private Object[] objectsWithTools;
    private Function<Cognisphere, String> contextProvider;
    private String[] agentNames;

    public AgentBuilder(Class<T> agentServiceClass, Method agenticMethod) {
        this.agentServiceClass = agentServiceClass;

        Agent agent = agenticMethod.getAnnotation(Agent.class);
        if (agent == null) {
            throw new IllegalArgumentException("Method " + agenticMethod + " is not annotated with @Agent");
        }
        if (!agent.outputName().isEmpty()) {
            outputName = agent.outputName();
        }
    }

    public T build() {
        return build(null);
    }

    T build(DefaultCognisphere cognisphere) {
        AiServiceContext context = new AiServiceContext(agentServiceClass);
        AiServices<T> aiServices = AiServices.builder(context);
        if (model != null) {
            aiServices.chatModel(model);
        }
        if (chatMemory != null) {
            aiServices.chatMemory(chatMemory);
        }
        if (chatMemoryProvider != null) {
            aiServices.chatMemoryProvider(chatMemoryProvider);
        }
        if (objectsWithTools != null) {
            aiServices.tools(objectsWithTools);
        }

        boolean cognisphereDependent = contextProvider != null || (agentNames != null && agentNames.length > 0);
        if (cognisphere != null && cognisphereDependent) {
            if (contextProvider != null) {
                aiServices.chatRequestTransformer(new Context.CognisphereContextGenerator(cognisphere, contextProvider));
            } else {
                aiServices.chatRequestTransformer(new Context.Summarizer(cognisphere, model, agentNames));
            }
        }

        return (T) Proxy.newProxyInstance(
                agentServiceClass.getClassLoader(),
                new Class<?>[]{agentServiceClass, AgentInstance.class, ChatMemoryAccess.class, CognisphereOwner.class},
                new AgentInvocationHandler(context, aiServices.build(), this, cognisphereDependent));
    }

    String agentId() {
        return agentServiceClass.getName();
    }

    public AgentBuilder<T> chatModel(ChatModel model) {
        this.model = model;
        return this;
    }

    public AgentBuilder<T> chatMemory(ChatMemory chatMemory) {
        this.chatMemory = chatMemory;
        return this;
    }

    public AgentBuilder<T> chatMemoryProvider(ChatMemoryProvider chatMemoryProvider) {
        this.chatMemoryProvider = chatMemoryProvider;
        return this;
    }

    public AgentBuilder<T> tools(Object... objectsWithTools) {
        this.objectsWithTools = objectsWithTools;
        return this;
    }

    public AgentBuilder<T> outputName(String outputName) {
        this.outputName = outputName;
        return this;
    }

    public AgentBuilder<T> context(Function<Cognisphere, String> contextProvider) {
        this.contextProvider = contextProvider;
        return this;
    }

    public AgentBuilder<T> summarizedContext(String... agentNames) {
        this.agentNames = agentNames;
        return this;
    }
}
