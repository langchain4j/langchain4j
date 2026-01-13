package dev.langchain4j.agentic.agent;

import static dev.langchain4j.agentic.declarative.DeclarativeUtil.configureAgent;
import static dev.langchain4j.agentic.internal.AgentUtil.argumentsFromMethod;
import static dev.langchain4j.agentic.internal.AgentUtil.keyName;
import static dev.langchain4j.internal.Utils.isNullOrBlank;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agentic.Agent;
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

public class AgentBuilder<T> {
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

    public AgentBuilder(Class<T> agentServiceClass, Method agenticMethod) {
        this.agentServiceClass = agentServiceClass;
        this.agenticMethod = agenticMethod;
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
        this.arguments = argumentsFromMethod(agenticMethod, defaultValues);

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

        return (T) Proxy.newProxyInstance(
                agentServiceClass.getClassLoader(),
                new Class<?>[] {
                    agentServiceClass,
                    InternalAgent.class, AgenticScopeOwner.class,
                    ChatMemoryAccess.class, ChatMessagesAccess.class
                },
                new AgentInvocationHandler(context, aiServices.build(), this, messageRecorder, agenticScopeDependent));
    }

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

    public AgentBuilder<T> chatModel(ChatModel model) {
        this.model = model;
        return this;
    }

    public AgentBuilder<T> streamingChatModel(StreamingChatModel streamingChatModel) {
        this.streamingChatModel = streamingChatModel;
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

    boolean hasNonDefaultChatMemory() {
        return chatMemoryProvider != null;
    }

    public AgentBuilder<T> tools(Object... objectsWithTools) {
        this.objectsWithTools = objectsWithTools;
        return this;
    }

    public AgentBuilder<T> tools(Map<ToolSpecification, ToolExecutor> toolsMap) {
        this.toolsMap = toolsMap;
        return this;
    }

    public AgentBuilder<T> tools(Map<ToolSpecification, ToolExecutor> toolsMap, Set<String> immediateReturnToolNames) {
        this.toolsMap = toolsMap;
        this.immediateReturnToolNames = immediateReturnToolNames;
        return this;
    }

    public AgentBuilder<T> toolProvider(ToolProvider toolProvider) {
        this.toolProvider = toolProvider;
        return this;
    }

    public AgentBuilder<T> maxSequentialToolsInvocations(int maxSequentialToolsInvocations) {
        this.maxSequentialToolsInvocations = maxSequentialToolsInvocations;
        return this;
    }

    public AgentBuilder<T> hallucinatedToolNameStrategy(
            Function<ToolExecutionRequest, ToolExecutionResultMessage> hallucinatedToolNameStrategy) {
        this.hallucinatedToolNameStrategy = hallucinatedToolNameStrategy;
        return this;
    }

    public AgentBuilder<T> contentRetriever(ContentRetriever contentRetriever) {
        this.contentRetriever = contentRetriever;
        return this;
    }

    public AgentBuilder<T> retrievalAugmentor(RetrievalAugmentor retrievalAugmentor) {
        this.retrievalAugmentor = retrievalAugmentor;
        return this;
    }

    public AgentBuilder<T> inputGuardrailsConfig(InputGuardrailsConfig inputGuardrailsConfig) {
        this.inputGuardrailsConfig = inputGuardrailsConfig;
        return this;
    }

    public AgentBuilder<T> outputGuardrailsConfig(OutputGuardrailsConfig outputGuardrailsConfig) {
        this.outputGuardrailsConfig = outputGuardrailsConfig;
        return this;
    }

    public <I extends InputGuardrail> AgentBuilder<T> inputGuardrailClasses(
            Class<? extends I>... inputGuardrailClasses) {
        this.inputGuardrailClasses = inputGuardrailClasses;
        return this;
    }

    public <O extends OutputGuardrail> AgentBuilder<T> outputGuardrailClasses(
            Class<? extends O>... outputGuardrailClasses) {
        this.outputGuardrailClasses = outputGuardrailClasses;
        return this;
    }

    public <I extends InputGuardrail> AgentBuilder<T> inputGuardrails(I... inputGuardrails) {
        this.inputGuardrails = inputGuardrails;
        return this;
    }

    public <O extends OutputGuardrail> AgentBuilder<T> outputGuardrails(O... outputGuardrails) {
        this.outputGuardrails = outputGuardrails;
        return this;
    }

    public AgentBuilder<T> name(String name) {
        this.name = name;
        return this;
    }

    public AgentBuilder<T> description(String description) {
        this.description = description;
        return this;
    }

    public AgentBuilder<T> outputKey(String outputKey) {
        this.outputKey = outputKey;
        return this;
    }

    public AgentBuilder<T> outputKey(Class<? extends TypedKey<?>> outputKey) {
        return outputKey(keyName(outputKey));
    }

    public AgentBuilder<T> async(boolean async) {
        this.async = async;
        return this;
    }

    public AgentBuilder<T> context(Function<AgenticScope, String> contextProvider) {
        this.contextProvider = contextProvider;
        return this;
    }

    public AgentBuilder<T> summarizedContext(String... contextProvidingAgents) {
        this.contextProvidingAgents = contextProvidingAgents;
        return this;
    }

    public AgentBuilder<T> systemMessageProvider(Function<Object, String> systemMessageProvider) {
        this.systemMessageProvider = systemMessageProvider;
        return this;
    }

    public AgentBuilder<T> executeToolsConcurrently() {
        this.executeToolsConcurrently = true;
        return this;
    }

    public AgentBuilder<T> executeToolsConcurrently(Executor executor) {
        this.executeToolsConcurrently = true;
        this.concurrentToolsExecutor = executor;
        return this;
    }

    public AgentBuilder<T> toolArgumentsErrorHandler(ToolArgumentsErrorHandler toolArgumentsErrorHandler) {
        this.toolArgumentsErrorHandler = toolArgumentsErrorHandler;
        return this;
    }

    public AgentBuilder<T> toolExecutionErrorHandler(ToolExecutionErrorHandler toolExecutionErrorHandler) {
        this.toolExecutionErrorHandler = toolExecutionErrorHandler;
        return this;
    }

    public AgentBuilder<T> defaultKeyValue(String key, Object value) {
        this.defaultValues.put(key, value);
        return this;
    }

    public <K> AgentBuilder<T> defaultKeyValue(Class<? extends TypedKey<K>> key, K value) {
        return defaultKeyValue(keyName(key), value);
    }

    public AgentBuilder<T> listener(AgentListener agentListener) {
        if (this.agentListener == null) {
            this.agentListener = agentListener;
        } else if (this.agentListener instanceof ComposedAgentListener composed) {
            composed.addListener(agentListener);
        } else {
            this.agentListener = new ComposedAgentListener(this.agentListener, agentListener);
        }
        return this;
    }

}
