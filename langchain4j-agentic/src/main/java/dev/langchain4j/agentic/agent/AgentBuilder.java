package dev.langchain4j.agentic.agent;

import static dev.langchain4j.agentic.declarative.DeclarativeUtil.configureAgent;
import static dev.langchain4j.agentic.internal.AgentUtil.argumentsFromMethod;
import static dev.langchain4j.agentic.internal.AgentUtil.keyName;
import static dev.langchain4j.agentic.internal.AgentUtil.validateAgentClass;
import static dev.langchain4j.internal.Utils.isNullOrBlank;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.K;
import dev.langchain4j.agentic.declarative.TypedKey;
import dev.langchain4j.agentic.internal.InternalAgent;
import dev.langchain4j.agentic.observability.AgentListener;
import dev.langchain4j.agentic.observability.ComposedAgentListener;
import dev.langchain4j.agentic.internal.AgentUtil;
import dev.langchain4j.agentic.internal.AgenticScopeOwner;
import dev.langchain4j.agentic.internal.Context;
import dev.langchain4j.agentic.internal.UserMessageRecorder;
import dev.langchain4j.agentic.planner.AgentArgument;
import dev.langchain4j.agentic.planner.AgenticSystemConfigurationException;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.agentic.scope.DefaultAgenticScope;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.guardrail.InputGuardrail;
import dev.langchain4j.guardrail.OutputGuardrail;
import dev.langchain4j.guardrail.config.InputGuardrailsConfig;
import dev.langchain4j.guardrail.config.OutputGuardrailsConfig;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.service.AiServiceContext;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.memory.ChatMemoryAccess;
import dev.langchain4j.service.tool.ToolArgumentsErrorHandler;
import dev.langchain4j.service.tool.ToolExecutionErrorHandler;
import dev.langchain4j.service.tool.ToolExecutor;
import dev.langchain4j.service.tool.ToolProvider;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.function.Function;

public class AgentBuilder<T, B extends AgentBuilder<T, ?>> {
    final Class<T> agentServiceClass;
    final Method agenticMethod;
    final Class<?> agentReturnType;
    List<AgentArgument> arguments;

    String name;
    String description;
    String outputKey;
    boolean async;

    private final Map<String, Object> defaultValues = new HashMap<>();

    private ChatModel model;
    private StreamingChatModel streamingChatModel;
    private ChatMemory chatMemory;
    private ChatMemoryProvider chatMemoryProvider;
    private Function<AgenticScope, String> contextProvider;
    private String[] contextProvidingAgents;
    private ContentRetriever contentRetriever;
    private RetrievalAugmentor retrievalAugmentor;
    private Function<Object, String> systemMessageProvider;
    private Function<Object, String> userMessageProvider;

    private InputGuardrailsConfig inputGuardrailsConfig;
    private OutputGuardrailsConfig outputGuardrailsConfig;
    private Class<? extends InputGuardrail>[] inputGuardrailClasses;
    private Class<? extends OutputGuardrail>[] outputGuardrailClasses;
    private InputGuardrail[] inputGuardrails;
    private OutputGuardrail[] outputGuardrails;

    private Object[] objectsWithTools;
    private Map<ToolSpecification, ToolExecutor> toolsMap;
    private Set<String> immediateReturnToolNames;
    private ToolProvider toolProvider;
    private Integer maxSequentialToolsInvocations;
    private Function<ToolExecutionRequest, ToolExecutionResultMessage> hallucinatedToolNameStrategy;
    private boolean executeToolsConcurrently;
    private Executor concurrentToolsExecutor;
    private ToolArgumentsErrorHandler toolArgumentsErrorHandler;
    private ToolExecutionErrorHandler toolExecutionErrorHandler;

    AgentListener agentListener;

    public AgentBuilder(Class<T> agentServiceClass) {
        this.agentServiceClass = agentServiceClass;
        this.agenticMethod = validateAgentClass(agentServiceClass);
        this.agentReturnType = agenticMethod.getReturnType();

        Agent agent = agenticMethod.getAnnotation(Agent.class);
        if (agent == null) {
            throw new IllegalArgumentException("Method " + agenticMethod + " is not annotated with @Agent");
        }

        configureAgent(agentServiceClass, this);

        this.name = !isNullOrBlank(agent.name()) ? agent.name() : agenticMethod.getName();

        if (!isNullOrBlank(agent.description())) {
            this.description = agent.description();
        } else if (!isNullOrBlank(agent.value())) {
            this.description = agent.value();
        }

        this.outputKey = AgentUtil.outputKey(agent.outputKey(), agent.typedOutputKey());

        this.async = agent.async();
        if (agent.summarizedContext() != null && agent.summarizedContext().length > 0) {
            this.contextProvidingAgents = agent.summarizedContext();
        }
    }

    public T build() {
        return build(null);
    }

    T build(DefaultAgenticScope agenticScope) {
        if (this.arguments == null) {
            this.arguments = argumentsFromMethod(agenticMethod, defaultValues);
        }

        AiServiceContext context = AiServiceContext.create(agentServiceClass);
        AiServices<T> aiServices = AiServices.builder(context);
        if (model != null && streamingChatModel != null) {
            throw new AgenticSystemConfigurationException(
                    "Both chatModel and streamingChatModel are set for agent '" + this.name + "'. Please set only one of them.");
        }
        if (model != null) {
            aiServices.chatModel(model);
        }
        if (streamingChatModel != null) {
            aiServices.streamingChatModel(streamingChatModel);
        }
        if (chatMemory != null) {
            aiServices.chatMemory(chatMemory);
        }
        if (chatMemoryProvider != null) {
            aiServices.chatMemoryProvider(chatMemoryProvider);
        }
        if (systemMessageProvider != null) {
            aiServices.systemMessageProvider(systemMessageProvider);
        }
        if (userMessageProvider != null) {
            aiServices.userMessageProvider(userMessageProvider);
        }
        if (contentRetriever != null) {
            aiServices.contentRetriever(contentRetriever);
        }
        if (retrievalAugmentor != null) {
            aiServices.retrievalAugmentor(retrievalAugmentor);
        }
        if (agentListener != null) {
            aiServices.beforeToolExecution(agentListener::beforeToolExecution);
            aiServices.afterToolExecution(agentListener::afterToolExecution);
        }

        setupGuardrails(aiServices);
        setupTools(aiServices);

        UserMessageRecorder messageRecorder = new UserMessageRecorder();
        boolean agenticScopeDependent =
                contextProvider != null || (contextProvidingAgents != null && contextProvidingAgents.length > 0);
        if (agenticScope != null && agenticScopeDependent) {
            if (contextProvider != null) {
                aiServices.chatRequestTransformer(
                        new Context.AgenticScopeContextGenerator(agenticScope, contextProvider)
                                .andThen(messageRecorder));
            } else {
                aiServices.chatRequestTransformer(
                        new Context.Summarizer(agenticScope, model, contextProvidingAgents).andThen(messageRecorder));
            }
        } else {
            aiServices.chatRequestTransformer(messageRecorder);
        }

        build(agenticScope, context, aiServices);

        return (T) Proxy.newProxyInstance(
                agentServiceClass.getClassLoader(),
                new Class<?>[] {
                    agentServiceClass,
                    InternalAgent.class, AgenticScopeOwner.class,
                    ChatMemoryAccess.class, ChatMessagesAccess.class
                },
                new AgentInvocationHandler(context, aiServices.build(), this, messageRecorder, agenticScopeDependent));
    }

    protected void build(DefaultAgenticScope agenticScope, AiServiceContext context, AiServices<T> aiServices) { }

    private void setupGuardrails(AiServices<T> aiServices) {
        if (inputGuardrailsConfig != null) {
            aiServices.inputGuardrailsConfig(inputGuardrailsConfig);
        }
        if (outputGuardrailsConfig != null) {
            aiServices.outputGuardrailsConfig(outputGuardrailsConfig);
        }
        if (inputGuardrailClasses != null) {
            aiServices.inputGuardrailClasses(inputGuardrailClasses);
        }
        if (outputGuardrailClasses != null) {
            aiServices.outputGuardrailClasses(outputGuardrailClasses);
        }
        if (inputGuardrails != null) {
            aiServices.inputGuardrails(inputGuardrails);
        }
        if (outputGuardrails != null) {
            aiServices.outputGuardrails(outputGuardrails);
        }
    }

    private void setupTools(AiServices<T> aiServices) {
        if (objectsWithTools != null) {
            aiServices.tools(objectsWithTools);
        }
        if (toolsMap != null) {
            if (immediateReturnToolNames != null) {
                aiServices.tools(toolsMap, immediateReturnToolNames);
            } else {
                aiServices.tools(toolsMap);
            }
        }
        if (toolProvider != null) {
            aiServices.toolProvider(toolProvider);
        }
        if (maxSequentialToolsInvocations != null) {
            aiServices.maxSequentialToolsInvocations(maxSequentialToolsInvocations);
        }
        if (hallucinatedToolNameStrategy != null) {
            aiServices.hallucinatedToolNameStrategy(hallucinatedToolNameStrategy);
        }
        if (executeToolsConcurrently) {
            if (concurrentToolsExecutor != null) {
                aiServices.executeToolsConcurrently(concurrentToolsExecutor);
            } else {
                aiServices.executeToolsConcurrently();
            }
        }
        if (toolArgumentsErrorHandler != null) {
            aiServices.toolArgumentsErrorHandler(toolArgumentsErrorHandler);
        }
        if (toolExecutionErrorHandler != null) {
            aiServices.toolExecutionErrorHandler(toolExecutionErrorHandler);
        }
    }

    public B chatModel(ChatModel model) {
        this.model = model;
        return (B) this;
    }

    public B streamingChatModel(StreamingChatModel streamingChatModel) {
        this.streamingChatModel = streamingChatModel;
        return (B) this;
    }

    public B chatMemory(ChatMemory chatMemory) {
        this.chatMemory = chatMemory;
        return (B) this;
    }

    public B chatMemoryProvider(ChatMemoryProvider chatMemoryProvider) {
        this.chatMemoryProvider = chatMemoryProvider;
        return (B) this;
    }

    boolean hasNonDefaultChatMemory() {
        return chatMemoryProvider != null;
    }

    public B tools(Object... objectsWithTools) {
        this.objectsWithTools = objectsWithTools;
        return (B) this;
    }

    public B tools(Map<ToolSpecification, ToolExecutor> toolsMap) {
        this.toolsMap = toolsMap;
        return (B) this;
    }

    public B tools(Map<ToolSpecification, ToolExecutor> toolsMap, Set<String> immediateReturnToolNames) {
        this.toolsMap = toolsMap;
        this.immediateReturnToolNames = immediateReturnToolNames;
        return (B) this;
    }

    public B toolProvider(ToolProvider toolProvider) {
        this.toolProvider = toolProvider;
        return (B) this;
    }

    public B maxSequentialToolsInvocations(int maxSequentialToolsInvocations) {
        this.maxSequentialToolsInvocations = maxSequentialToolsInvocations;
        return (B) this;
    }

    public B hallucinatedToolNameStrategy(
            Function<ToolExecutionRequest, ToolExecutionResultMessage> hallucinatedToolNameStrategy) {
        this.hallucinatedToolNameStrategy = hallucinatedToolNameStrategy;
        return (B) this;
    }

    public B contentRetriever(ContentRetriever contentRetriever) {
        this.contentRetriever = contentRetriever;
        return (B) this;
    }

    public B retrievalAugmentor(RetrievalAugmentor retrievalAugmentor) {
        this.retrievalAugmentor = retrievalAugmentor;
        return (B) this;
    }

    public B inputGuardrailsConfig(InputGuardrailsConfig inputGuardrailsConfig) {
        this.inputGuardrailsConfig = inputGuardrailsConfig;
        return (B) this;
    }

    public B outputGuardrailsConfig(OutputGuardrailsConfig outputGuardrailsConfig) {
        this.outputGuardrailsConfig = outputGuardrailsConfig;
        return (B) this;
    }

    public <I extends InputGuardrail> B inputGuardrailClasses(
            Class<? extends I>... inputGuardrailClasses) {
        this.inputGuardrailClasses = inputGuardrailClasses;
        return (B) this;
    }

    public <O extends OutputGuardrail> B outputGuardrailClasses(
            Class<? extends O>... outputGuardrailClasses) {
        this.outputGuardrailClasses = outputGuardrailClasses;
        return (B) this;
    }

    public <I extends InputGuardrail> B inputGuardrails(I... inputGuardrails) {
        this.inputGuardrails = inputGuardrails;
        return (B) this;
    }

    public <O extends OutputGuardrail> B outputGuardrails(O... outputGuardrails) {
        this.outputGuardrails = outputGuardrails;
        return (B) this;
    }

    public B name(String name) {
        this.name = name;
        return (B) this;
    }

    public B description(String description) {
        this.description = description;
        return (B) this;
    }

    public B outputKey(String outputKey) {
        this.outputKey = outputKey;
        return (B) this;
    }

    public B outputKey(Class<? extends TypedKey<?>> outputKey) {
        return outputKey(keyName(outputKey));
    }

    public B async(boolean async) {
        this.async = async;
        return (B) this;
    }

    public B context(Function<AgenticScope, String> contextProvider) {
        this.contextProvider = contextProvider;
        return (B) this;
    }

    public B summarizedContext(String... contextProvidingAgents) {
        this.contextProvidingAgents = contextProvidingAgents;
        return (B) this;
    }

    public B systemMessage(String systemMessage) {
        return systemMessageProvider(ignore -> systemMessage);
    }

    public B systemMessageProvider(Function<Object, String> systemMessageProvider) {
        this.systemMessageProvider = systemMessageProvider;
        return (B) this;
    }

    public B userMessage(String userMessage) {
        return userMessageProvider(ignore -> userMessage);
    }

    public B userMessageProvider(Function<Object, String> userMessageProvider) {
        this.userMessageProvider = userMessageProvider;
        return (B) this;
    }

    public B executeToolsConcurrently() {
        this.executeToolsConcurrently = true;
        return (B) this;
    }

    public B executeToolsConcurrently(Executor executor) {
        this.executeToolsConcurrently = true;
        this.concurrentToolsExecutor = executor;
        return (B) this;
    }

    public B toolArgumentsErrorHandler(ToolArgumentsErrorHandler toolArgumentsErrorHandler) {
        this.toolArgumentsErrorHandler = toolArgumentsErrorHandler;
        return (B) this;
    }

    public B toolExecutionErrorHandler(ToolExecutionErrorHandler toolExecutionErrorHandler) {
        this.toolExecutionErrorHandler = toolExecutionErrorHandler;
        return (B) this;
    }

    public B defaultKeyValue(String key, Object value) {
        this.defaultValues.put(key, value);
        return (B) this;
    }

    public <K> B defaultKeyValue(Class<? extends TypedKey<K>> key, K value) {
        return defaultKeyValue(keyName(key), value);
    }

    public B listener(AgentListener agentListener) {
        if (this.agentListener == null) {
            this.agentListener = agentListener;
        } else if (this.agentListener instanceof ComposedAgentListener composed) {
            composed.addListener(agentListener);
        } else {
            this.agentListener = new ComposedAgentListener(this.agentListener, agentListener);
        }
        return (B) this;
    }

}
