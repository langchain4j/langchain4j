package dev.langchain4j.agentic.agent;

import static dev.langchain4j.agentic.declarative.DeclarativeUtil.configureAgent;
import static dev.langchain4j.agentic.internal.AgentUtil.argumentsFromMethod;
import static dev.langchain4j.agentic.internal.AgentUtil.keyName;
import static dev.langchain4j.agentic.internal.AgentUtil.validateAgentClass;
import static dev.langchain4j.agentic.observability.ComposedAgentListener.listenerOfType;
import static dev.langchain4j.internal.Utils.isNullOrBlank;
import static java.util.Arrays.asList;

import dev.langchain4j.Internal;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.TypedKey;
import dev.langchain4j.agentic.internal.AgentUtil;
import dev.langchain4j.agentic.internal.AgenticScopeOwner;
import dev.langchain4j.agentic.internal.Context;
import dev.langchain4j.agentic.internal.InternalAgent;
import dev.langchain4j.agentic.observability.AfterAgentToolExecution;
import dev.langchain4j.agentic.observability.AgentListener;
import dev.langchain4j.agentic.observability.AgentMonitor;
import dev.langchain4j.agentic.observability.BeforeAgentToolExecution;
import dev.langchain4j.agentic.observability.ComposedAgentListener;
import dev.langchain4j.agentic.observability.MonitoredAgent;
import dev.langchain4j.agentic.planner.AgentArgument;
import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.planner.AgenticSystemConfigurationException;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.agentic.scope.DefaultAgenticScope;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.guardrail.InputGuardrail;
import dev.langchain4j.guardrail.OutputGuardrail;
import dev.langchain4j.guardrail.config.InputGuardrailsConfig;
import dev.langchain4j.guardrail.config.OutputGuardrailsConfig;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.observability.api.listener.AiServiceResponseReceivedListener;
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
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.UnaryOperator;

public class AgentBuilder<T, B extends AgentBuilder<T, ?>> {

    private static final ChatModel PLACEHOLDER_CHAT_MODEL = new ChatModel() {
        @Override
        public ChatResponse doChat(ChatRequest chatRequest) {
            throw new IllegalStateException("Placeholder ChatModel should never be invoked. "
                    + "The actual model is provided dynamically via the chatModel(Function) provider.");
        }
    };

    private static final StreamingChatModel PLACEHOLDER_STREAMING_CHAT_MODEL = new StreamingChatModel() {
        @Override
        public void doChat(ChatRequest chatRequest, StreamingChatResponseHandler handler) {
            throw new IllegalStateException("Placeholder StreamingChatModel should never be invoked. "
                    + "The actual model is provided dynamically via the streamingChatModel(Function) provider.");
        }
    };

    final Class<T> agentServiceClass;
    final Method agenticMethod;
    final Class<?> agentReturnType;
    List<AgentArgument> arguments;

    String name;
    String description;
    String outputKey;
    boolean async;
    boolean optional;

    private final Map<String, Object> defaultValues = new HashMap<>();

    private ChatModel model;
    private StreamingChatModel streamingChatModel;
    Function<AgenticScope, ChatModel> chatModelProvider;
    Function<AgenticScope, StreamingChatModel> streamingChatModelProvider;
    private ChatMemory chatMemory;
    private ChatMemoryProvider chatMemoryProvider;
    private Function<AgenticScope, String> contextProvider;
    private String[] contextProvidingAgents;
    private ContentRetriever contentRetriever;
    private RetrievalAugmentor retrievalAugmentor;
    private Function<Object, String> systemMessageProvider;
    private BiFunction<String, InvocationContext, String> systemMessageTransformer;
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
    private final List<ToolProvider> toolProviders = new ArrayList<>();
    private Integer maxToolCallingRoundTrips;
    private Function<ToolExecutionRequest, ToolExecutionResultMessage> hallucinatedToolNameStrategy;
    private boolean executeToolsConcurrently;
    private Executor concurrentToolsExecutor;
    private ToolArgumentsErrorHandler toolArgumentsErrorHandler;
    private ToolExecutionErrorHandler toolExecutionErrorHandler;

    java.util.function.Function<InternalAgent, Object> agentInstanceFactory;

    AgentListener agentListener;

    public AgentBuilder(Class<T> agentServiceClass) {
        this(agentServiceClass, true);
    }

    @Internal
    public static <T> AgentBuilder<T, AgentBuilder<T, ?>> withoutDeclarativeConfiguration(Class<T> agentServiceClass) {
        return new AgentBuilder<>(agentServiceClass, false);
    }

    private AgentBuilder(Class<T> agentServiceClass, boolean configureDeclarativeAgent) {
        this.agentServiceClass = agentServiceClass;
        this.agenticMethod = validateAgentClass(agentServiceClass);
        this.agentReturnType = agenticMethod.getReturnType();

        Agent agent = agenticMethod.getAnnotation(Agent.class);
        if (agent == null) {
            throw new IllegalArgumentException("Method " + agenticMethod + " is not annotated with @Agent");
        }

        if (configureDeclarativeAgent) {
            configureAgent(agentServiceClass, this);
        }

        this.name = !isNullOrBlank(agent.name()) ? agent.name() : agenticMethod.getName();

        if (!isNullOrBlank(agent.description())) {
            this.description = agent.description();
        } else if (!isNullOrBlank(agent.value())) {
            this.description = agent.value();
        }

        this.outputKey = AgentUtil.outputKey(agent.outputKey(), agent.typedOutputKey());

        this.async = agent.async();
        this.optional = agent.optional();
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

        configureChatModel(aiServices);

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
        if (systemMessageTransformer != null) {
            aiServices.systemMessageTransformer(systemMessageTransformer);
        }

        setupGuardrails(aiServices);
        setupTools(aiServices);

        boolean agenticScopeDependent =
                contextProvider != null || (contextProvidingAgents != null && contextProvidingAgents.length > 0);
        if (agenticScope != null && agenticScopeDependent) {
            if (contextProvider != null) {
                aiServices.chatRequestTransformer(
                        new Context.AgenticScopeContextGenerator(agenticScope, contextProvider));
            } else {
                aiServices.chatRequestTransformer(new Context.Summarizer(agenticScope, model, contextProvidingAgents));
            }
        }

        AgentMonitor monitor = listenerOfType(agentListener, AgentMonitor.class);
        if (MonitoredAgent.class.isAssignableFrom(agentServiceClass) && monitor == null) {
            monitor = new AgentMonitor();
            listener(monitor);
        }

        build(agenticScope, context, aiServices);

        AgentInvocationHandler handler =
                new AgentInvocationHandler(context, aiServices.build(), this, agenticScopeDependent);

        AgentInstance agent;
        if (agentInstanceFactory != null) {
            agent = (AgentInstance) agentInstanceFactory.apply(handler);
        } else {
            agent = (AgentInstance) Proxy.newProxyInstance(
                    agentServiceClass.getClassLoader(), interfacesToImplement(agentServiceClass), handler);
        }

        aiServices.registerListener((AiServiceResponseReceivedListener) agent);

        if (monitor != null) {
            monitor.setRootAgent(agent);
        }

        if (agentListener != null) {
            aiServices.beforeToolExecution(beforeToolExecution ->
                    agentListener.beforeAgentToolExecution(new BeforeAgentToolExecution(agent, beforeToolExecution)));
            aiServices.afterToolExecution(afterToolExecution ->
                    agentListener.afterAgentToolExecution(new AfterAgentToolExecution(agent, afterToolExecution)));
        }

        return (T) agent;
    }

    @SuppressWarnings("rawtypes")
    public static Class[] interfacesToImplement(Class clazz) {
        return new Class<?>[] {
            clazz,
            InternalAgent.class,
            AgenticScopeOwner.class,
            ChatMemoryAccess.class,
            ChatMessagesAccess.class,
            AiServiceResponseReceivedListener.class
        };
    }

    private void configureChatModel(AiServices<T> aiServices) {
        validateChatModel();
        if (model != null) {
            aiServices.chatModel(model);
        } else if (streamingChatModel != null) {
            aiServices.streamingChatModel(streamingChatModel);
        } else if (chatModelProvider != null) {
            aiServices.chatModel(PLACEHOLDER_CHAT_MODEL);
        } else if (streamingChatModelProvider != null) {
            aiServices.streamingChatModel(PLACEHOLDER_STREAMING_CHAT_MODEL);
        } else {
            throw new AgenticSystemConfigurationException("No chat model is configured for agent '" + this.name + "'.");
        }
    }

    private void validateChatModel() {
        int modelConfigCount = (model != null ? 1 : 0)
                + (streamingChatModel != null ? 1 : 0)
                + (chatModelProvider != null ? 1 : 0)
                + (streamingChatModelProvider != null ? 1 : 0);
        if (modelConfigCount != 1) {
            throw new AgenticSystemConfigurationException(
                    "One and only one of chatModel, streamingChatModel, or their Function variants can be set for agent '"
                            + this.name + "'.");
        }
    }

    protected void build(DefaultAgenticScope agenticScope, AiServiceContext context, AiServices<T> aiServices) {}

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
        if (!toolProviders.isEmpty()) {
            aiServices.toolProviders(toolProviders);
        }
        if (maxToolCallingRoundTrips != null) {
            aiServices.maxToolCallingRoundTrips(maxToolCallingRoundTrips);
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

    /**
     * Sets the {@link ChatModel} used by this agent.
     *
     * @param model the chat model
     * @return {@code this}
     */
    public B chatModel(ChatModel model) {
        this.model = model;
        return (B) this;
    }

    /**
     * Sets the {@link StreamingChatModel} used by this agent.
     *
     * @param streamingChatModel the streaming chat model
     * @return {@code this}
     */
    public B streamingChatModel(StreamingChatModel streamingChatModel) {
        this.streamingChatModel = streamingChatModel;
        return (B) this;
    }

    /**
     * Sets a provider that resolves the {@link ChatModel} from the {@link AgenticScope} at execution time.
     *
     * @param chatModelProvider the chat model provider function
     * @return {@code this}
     */
    public B chatModel(Function<AgenticScope, ChatModel> chatModelProvider) {
        this.chatModelProvider = chatModelProvider;
        return (B) this;
    }

    /**
     * Sets a provider that resolves the {@link StreamingChatModel} from the {@link AgenticScope} at execution time.
     *
     * @param streamingChatModelProvider the streaming chat model provider function
     * @return {@code this}
     */
    public B streamingChatModel(Function<AgenticScope, StreamingChatModel> streamingChatModelProvider) {
        this.streamingChatModelProvider = streamingChatModelProvider;
        return (B) this;
    }

    /**
     * Sets the {@link ChatMemory} used to maintain conversation history for this agent.
     *
     * @param chatMemory the chat memory
     * @return {@code this}
     */
    public B chatMemory(ChatMemory chatMemory) {
        this.chatMemory = chatMemory;
        return (B) this;
    }

    /**
     * Sets a {@link ChatMemoryProvider} for creating per-user or per-session chat memory instances.
     *
     * @param chatMemoryProvider the chat memory provider
     * @return {@code this}
     */
    public B chatMemoryProvider(ChatMemoryProvider chatMemoryProvider) {
        this.chatMemoryProvider = chatMemoryProvider;
        return (B) this;
    }

    boolean hasChatMemory() {
        return chatMemory != null || chatMemoryProvider != null;
    }

    boolean hasNonDefaultChatMemory() {
        return chatMemoryProvider != null;
    }

    /**
     * Sets the tool objects whose {@code @Tool}-annotated methods will be available to this agent.
     *
     * @param objectsWithTools the objects containing tool methods
     * @return {@code this}
     */
    public B tools(Object... objectsWithTools) {
        this.objectsWithTools = objectsWithTools;
        return (B) this;
    }

    /**
     * Sets the tool specifications and their executors available to this agent.
     *
     * @param toolsMap the map of tool specifications to executors
     * @return {@code this}
     */
    public B tools(Map<ToolSpecification, ToolExecutor> toolsMap) {
        this.toolsMap = toolsMap;
        return (B) this;
    }

    /**
     * Sets the tool specifications and their executors, with a set of tool names that cause immediate return.
     *
     * @param toolsMap the map of tool specifications to executors
     * @param immediateReturnToolNames tool names whose results are returned immediately without further LLM calls
     * @return {@code this}
     */
    public B tools(Map<ToolSpecification, ToolExecutor> toolsMap, Set<String> immediateReturnToolNames) {
        this.toolsMap = toolsMap;
        this.immediateReturnToolNames = immediateReturnToolNames;
        return (B) this;
    }

    /**
     * Adds a {@link ToolProvider} that dynamically supplies tools to this agent.
     *
     * @param toolProvider the tool provider
     * @return {@code this}
     */
    public B toolProvider(ToolProvider toolProvider) {
        this.toolProviders.add(toolProvider);
        return (B) this;
    }

    /**
     * Adds multiple {@link ToolProvider} instances that dynamically supply tools to this agent.
     *
     * @param toolProviders the collection of tool providers
     * @return {@code this}
     */
    public B toolProviders(Collection<ToolProvider> toolProviders) {
        this.toolProviders.addAll(toolProviders);
        return (B) this;
    }

    /**
     * Adds multiple {@link ToolProvider} instances that dynamically supply tools to this agent (varargs overload).
     *
     * @param toolProviders the tool providers
     * @return {@code this}
     */
    public B toolProviders(ToolProvider... toolProviders) {
        return toolProviders(asList(toolProviders));
    }

    /**
     * Sets the maximum number of tool-calling round trips the agent may perform in a single invocation.
     *
     * @param maxToolCallingRoundTrips the maximum number of round trips
     * @return {@code this}
     */
    public B maxToolCallingRoundTrips(int maxToolCallingRoundTrips) {
        this.maxToolCallingRoundTrips = maxToolCallingRoundTrips;
        return (B) this;
    }

    /** @deprecated Use {@link #maxToolCallingRoundTrips(int)} instead. */
    @Deprecated(since = "1.15.0")
    public B maxSequentialToolsInvocations(int maxSequentialToolsInvocations) {
        return maxToolCallingRoundTrips(maxSequentialToolsInvocations);
    }

    /**
     * Sets the strategy for handling tool calls when the model hallucinates a non-existent tool name.
     *
     * @param hallucinatedToolNameStrategy the function that produces a result message for the hallucinated tool call
     * @return {@code this}
     */
    public B hallucinatedToolNameStrategy(
            Function<ToolExecutionRequest, ToolExecutionResultMessage> hallucinatedToolNameStrategy) {
        this.hallucinatedToolNameStrategy = hallucinatedToolNameStrategy;
        return (B) this;
    }

    /**
     * Sets the {@link ContentRetriever} used for RAG (Retrieval-Augmented Generation).
     *
     * @param contentRetriever the content retriever
     * @return {@code this}
     */
    public B contentRetriever(ContentRetriever contentRetriever) {
        this.contentRetriever = contentRetriever;
        return (B) this;
    }

    /**
     * Sets the {@link RetrievalAugmentor} for advanced RAG pipelines.
     *
     * @param retrievalAugmentor the retrieval augmentor
     * @return {@code this}
     */
    public B retrievalAugmentor(RetrievalAugmentor retrievalAugmentor) {
        this.retrievalAugmentor = retrievalAugmentor;
        return (B) this;
    }

    /**
     * Sets the configuration for input guardrails applied before sending requests to the model.
     *
     * @param inputGuardrailsConfig the input guardrails configuration
     * @return {@code this}
     */
    public B inputGuardrailsConfig(InputGuardrailsConfig inputGuardrailsConfig) {
        this.inputGuardrailsConfig = inputGuardrailsConfig;
        return (B) this;
    }

    /**
     * Sets the configuration for output guardrails applied after receiving responses from the model.
     *
     * @param outputGuardrailsConfig the output guardrails configuration
     * @return {@code this}
     */
    public B outputGuardrailsConfig(OutputGuardrailsConfig outputGuardrailsConfig) {
        this.outputGuardrailsConfig = outputGuardrailsConfig;
        return (B) this;
    }

    /**
     * Sets the input guardrail classes to be instantiated and applied before each request.
     *
     * @param inputGuardrailClasses the input guardrail classes
     * @param <I> the guardrail type
     * @return {@code this}
     */
    public <I extends InputGuardrail> B inputGuardrailClasses(Class<? extends I>... inputGuardrailClasses) {
        this.inputGuardrailClasses = inputGuardrailClasses;
        return (B) this;
    }

    /**
     * Sets the output guardrail classes to be instantiated and applied after each response.
     *
     * @param outputGuardrailClasses the output guardrail classes
     * @param <O> the guardrail type
     * @return {@code this}
     */
    public <O extends OutputGuardrail> B outputGuardrailClasses(Class<? extends O>... outputGuardrailClasses) {
        this.outputGuardrailClasses = outputGuardrailClasses;
        return (B) this;
    }

    /**
     * Sets the input guardrail instances applied before each request.
     *
     * @param inputGuardrails the input guardrails
     * @param <I> the guardrail type
     * @return {@code this}
     */
    public <I extends InputGuardrail> B inputGuardrails(I... inputGuardrails) {
        this.inputGuardrails = inputGuardrails;
        return (B) this;
    }

    /**
     * Sets the output guardrail instances applied after each response.
     *
     * @param outputGuardrails the output guardrails
     * @param <O> the guardrail type
     * @return {@code this}
     */
    public <O extends OutputGuardrail> B outputGuardrails(O... outputGuardrails) {
        this.outputGuardrails = outputGuardrails;
        return (B) this;
    }

    /**
     * Sets the agent name used for identification in multi-agent systems.
     *
     * @param name the agent name
     * @return {@code this}
     */
    public B name(String name) {
        this.name = name;
        return (B) this;
    }

    /**
     * Sets a human-readable description of what this agent does, used by planners and orchestrators.
     *
     * @param description the agent description
     * @return {@code this}
     */
    public B description(String description) {
        this.description = description;
        return (B) this;
    }

    /**
     * Sets the key under which this agent's output is stored in the {@link AgenticScope}.
     *
     * @param outputKey the output key name
     * @return {@code this}
     */
    public B outputKey(String outputKey) {
        this.outputKey = outputKey;
        return (B) this;
    }

    /**
     * Sets the output key using a {@link TypedKey} class for type-safe scope access.
     *
     * @param outputKey the typed key class
     * @return {@code this}
     */
    public B outputKey(Class<? extends TypedKey<?>> outputKey) {
        return outputKey(keyName(outputKey));
    }

    /**
     * Controls whether this agent executes asynchronously within an agentic system.
     *
     * @param async {@code true} to run asynchronously
     * @return {@code this}
     */
    public B async(boolean async) {
        this.async = async;
        return (B) this;
    }

    /**
     * Controls whether this agent is optional, allowing the system to skip it if it fails.
     *
     * @param optional {@code true} to mark the agent as optional
     * @return {@code this}
     */
    public B optional(boolean optional) {
        this.optional = optional;
        return (B) this;
    }

    /**
     * Sets a function that provides additional context from the {@link AgenticScope} to this agent's prompt.
     *
     * @param contextProvider the context provider function
     * @return {@code this}
     */
    public B context(Function<AgenticScope, String> contextProvider) {
        this.contextProvider = contextProvider;
        return (B) this;
    }

    /**
     * Sets the names of other agents whose outputs are summarized and injected as context into this agent's prompt.
     *
     * @param contextProvidingAgents the names of agents whose outputs to summarize
     * @return {@code this}
     */
    public B summarizedContext(String... contextProvidingAgents) {
        this.contextProvidingAgents = contextProvidingAgents;
        return (B) this;
    }

    /**
     * Sets a static system message for this agent.
     *
     * @param systemMessage the system message text
     * @return {@code this}
     */
    public B systemMessage(String systemMessage) {
        return systemMessageProvider(ignore -> systemMessage);
    }

    /**
     * Sets a function that dynamically provides the system message based on the user message object.
     *
     * @param systemMessageProvider the system message provider function
     * @return {@code this}
     */
    public B systemMessageProvider(Function<Object, String> systemMessageProvider) {
        this.systemMessageProvider = systemMessageProvider;
        return (B) this;
    }

    /**
     * Sets a static user message for this agent.
     *
     * @param userMessage the user message text
     * @return {@code this}
     */
    public B userMessage(String userMessage) {
        return userMessageProvider(ignore -> userMessage);
    }

    /**
     * Sets a function that dynamically provides the user message based on the user message object.
     *
     * @param userMessageProvider the user message provider function
     * @return {@code this}
     */
    public B userMessageProvider(Function<Object, String> userMessageProvider) {
        this.userMessageProvider = userMessageProvider;
        return (B) this;
    }

    /**
     * Sets a transformer that modifies the system message before it is sent to the model.
     *
     * @param systemMessageTransformer the system message transformer
     * @return {@code this}
     */
    public B systemMessageTransformer(UnaryOperator<String> systemMessageTransformer) {
        return systemMessageTransformer((msg, ctx) -> systemMessageTransformer.apply(msg));
    }

    /**
     * Sets a transformer that modifies the system message using both the message and the invocation context.
     *
     * @param systemMessageTransformer the system message transformer with context
     * @return {@code this}
     */
    public B systemMessageTransformer(BiFunction<String, InvocationContext, String> systemMessageTransformer) {
        this.systemMessageTransformer = systemMessageTransformer;
        return (B) this;
    }

    /**
     * Enables concurrent execution of tool calls using the default executor.
     *
     * @return {@code this}
     */
    public B executeToolsConcurrently() {
        this.executeToolsConcurrently = true;
        return (B) this;
    }

    /**
     * Enables concurrent execution of tool calls using the provided executor.
     *
     * @param executor the executor to use for concurrent tool execution
     * @return {@code this}
     */
    public B executeToolsConcurrently(Executor executor) {
        this.executeToolsConcurrently = true;
        this.concurrentToolsExecutor = executor;
        return (B) this;
    }

    /**
     * Sets the handler invoked when the model provides invalid arguments for a tool call.
     *
     * @param toolArgumentsErrorHandler the tool arguments error handler
     * @return {@code this}
     */
    public B toolArgumentsErrorHandler(ToolArgumentsErrorHandler toolArgumentsErrorHandler) {
        this.toolArgumentsErrorHandler = toolArgumentsErrorHandler;
        return (B) this;
    }

    /**
     * Sets the handler invoked when a tool execution throws an exception.
     *
     * @param toolExecutionErrorHandler the tool execution error handler
     * @return {@code this}
     */
    public B toolExecutionErrorHandler(ToolExecutionErrorHandler toolExecutionErrorHandler) {
        this.toolExecutionErrorHandler = toolExecutionErrorHandler;
        return (B) this;
    }

    /**
     * Sets a default value for an agent argument identified by key name.
     *
     * @param key the argument key name
     * @param value the default value
     * @return {@code this}
     */
    public B defaultKeyValue(String key, Object value) {
        this.defaultValues.put(key, value);
        return (B) this;
    }

    /**
     * Sets a default value for an agent argument identified by a {@link TypedKey} class.
     *
     * @param key the typed key class
     * @param value the default value
     * @param <K> the value type
     * @return {@code this}
     */
    public <K> B defaultKeyValue(Class<? extends TypedKey<K>> key, K value) {
        return defaultKeyValue(keyName(key), value);
    }

    /**
     * Sets a custom factory for creating the agent proxy instance.
     *
     * @param factory the agent instance factory
     * @return {@code this}
     */
    public B agentInstanceFactory(java.util.function.Function<InternalAgent, Object> factory) {
        this.agentInstanceFactory = factory;
        return (B) this;
    }

    /**
     * Adds an {@link AgentListener} for observability and lifecycle hooks.
     *
     * @param agentListener the agent listener
     * @return {@code this}
     */
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
