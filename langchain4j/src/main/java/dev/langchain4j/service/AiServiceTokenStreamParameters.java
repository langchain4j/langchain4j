package dev.langchain4j.service;

import dev.langchain4j.Internal;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.guardrail.GuardrailRequestParams;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.service.tool.ToolArgumentsErrorHandler;
import dev.langchain4j.service.tool.ToolExecutionErrorHandler;
import dev.langchain4j.service.tool.ToolExecutor;
import dev.langchain4j.service.tool.ToolServiceContext;
import dev.langchain4j.service.tool.search.ToolSearchStrategy;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * Parameters for creating an {@link AiServiceTokenStream}.
 */
@Internal
public class AiServiceTokenStreamParameters {

    private final List<ChatMessage> messages;
    private final ToolServiceContext toolServiceContext;
    private final ToolArgumentsErrorHandler toolArgumentsErrorHandler;
    private final ToolExecutionErrorHandler toolExecutionErrorHandler;
    private final Executor toolExecutor;
    private final Executor completeResponseProcessingExecutor;
    private final List<Content> retrievedContents;
    private final AiServiceContext context;
    private final InvocationContext invocationContext;
    private final GuardrailRequestParams commonGuardrailParams;
    private final Object methodKey;

    protected AiServiceTokenStreamParameters(Builder builder) {
        this.messages = builder.messages;
        this.toolServiceContext = builder.toolServiceContext;
        this.toolArgumentsErrorHandler = builder.toolArgumentsErrorHandler;
        this.toolExecutionErrorHandler = builder.toolExecutionErrorHandler;
        this.toolExecutor = builder.toolExecutor;
        this.completeResponseProcessingExecutor = builder.completeResponseProcessingExecutor;
        this.retrievedContents = builder.retrievedContents;
        this.context = builder.context;
        this.invocationContext = builder.invocationContext;
        this.commonGuardrailParams = builder.commonGuardrailParams;
        this.methodKey = builder.methodKey;
    }

    /**
     * Returns messages that should be included in the next {@link ChatRequest}.
     */
    public List<ChatMessage> messages() {
        return messages;
    }

    public ToolServiceContext toolServiceContext() {
        return toolServiceContext;
    }

    /**
     * Returns <b>effective</b> tool specifications that should be included in the next {@link ChatRequest}.
     *
     * @see #availableTools()
     * @deprecated use {@link #toolServiceContext()} instead
     */
    @Deprecated(since = "1.13.0")
    public List<ToolSpecification> effectiveTools() {
        return toolServiceContext != null ? toolServiceContext.effectiveTools() : null;
    }

    /**
     * Returns <b>effective</b> tool specifications that should be included in the next {@link ChatRequest}.
     *
     * @see #effectiveTools()
     * @deprecated use {@link #toolServiceContext()} instead
     */
    @Deprecated(since = "1.12.0")
    public List<ToolSpecification> toolSpecifications() {
        return effectiveTools();
    }

    /**
     * Returns <b>all available</b> tool specifications configured for AI service.
     * These tool specifications can be discovered/found by the LLM (see {@link ToolSearchStrategy})
     * and included in the next {@link ChatRequest}.
     *
     * @see #effectiveTools()
     * @deprecated use {@link #toolServiceContext()} instead
     */
    @Deprecated(since = "1.13.0")
    public List<ToolSpecification> availableTools() {
        return toolServiceContext != null ? toolServiceContext.availableTools() : null;
    }

    /**
     * @return the tool executors
     * @deprecated use {@link #toolServiceContext()} instead
     */
    @Deprecated(since = "1.13.0")
    public Map<String, ToolExecutor> toolExecutors() {
        return toolServiceContext != null ? toolServiceContext.toolExecutors() : null;
    }

    /**
     * @since 1.4.0
     */
    public ToolArgumentsErrorHandler toolArgumentsErrorHandler() {
        return toolArgumentsErrorHandler;
    }

    /**
     * @since 1.4.0
     */
    public ToolExecutionErrorHandler toolExecutionErrorHandler() {
        return toolExecutionErrorHandler;
    }

    /**
     * @since 1.4.0
     */
    public Executor toolExecutor() {
        return toolExecutor;
    }

    /**
     * @see Builder#completeResponseProcessingExecutor(Executor)
     * @since 1.23.0
     */
    public Executor completeResponseProcessingExecutor() {
        return completeResponseProcessingExecutor;
    }

    /**
     * @return the retrieved contents
     */
    public List<Content> retrievedContents() {
        return retrievedContents;
    }

    /**
     * @return the AI service context
     */
    public AiServiceContext context() {
        return context;
    }

    /**
     * @since 1.6.0
     */
    public InvocationContext invocationContext() {
        return invocationContext;
    }

    /**
     * Retrieves the common parameters shared across guardrail checks for validating interactions
     * between a user and a language model, if available.
     *
     * @return the {@link GuardrailRequestParams} containing chat memory, user message template,
     * and additional variables required for guardrail processing, or null if not set.
     */
    public GuardrailRequestParams commonGuardrailParams() {
        return commonGuardrailParams;
    }

    /**
     * Retrieves the method key associated with this instance.
     *
     * @return the method key as an Object
     */
    public Object methodKey() {
        return methodKey;
    }

    /**
     * Creates a new builder for {@link AiServiceTokenStreamParameters}.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link AiServiceTokenStreamParameters}.
     */
    public static class Builder {

        private List<ChatMessage> messages;
        private ToolServiceContext toolServiceContext;
        private ToolArgumentsErrorHandler toolArgumentsErrorHandler;
        private ToolExecutionErrorHandler toolExecutionErrorHandler;
        private Executor toolExecutor;
        private Executor completeResponseProcessingExecutor;
        private List<Content> retrievedContents;
        private AiServiceContext context;
        private InvocationContext invocationContext;
        private GuardrailRequestParams commonGuardrailParams;
        private Object methodKey;

        protected Builder() {}

        /**
         * Sets the messages.
         *
         * @param messages the messages
         * @return this builder
         */
        public Builder messages(List<ChatMessage> messages) {
            this.messages = messages;
            return this;
        }

        /**
         * Sets the tool service context.
         *
         * @since 1.13.0
         */
        public Builder toolServiceContext(ToolServiceContext toolServiceContext) {
            this.toolServiceContext = toolServiceContext;
            return this;
        }

        /**
         * Sets <b>effective</b> tool specifications that should be included in the next {@link ChatRequest}.
         *
         * @see #availableTools(List)
         * @deprecated use {@link #toolServiceContext(ToolServiceContext)} instead
         */
        @Deprecated(since = "1.13.0")
        public Builder effectiveTools(List<ToolSpecification> effectiveTools) {
            ensureToolServiceContext();
            this.toolServiceContext = this.toolServiceContext.toBuilder()
                    .effectiveTools(effectiveTools)
                    .build();
            return this;
        }

        /**
         * Sets tool specifications that should be included in the next {@link ChatRequest}.
         *
         * @deprecated use {@link #toolServiceContext(ToolServiceContext)} instead
         */
        @Deprecated(since = "1.12.0")
        public Builder toolSpecifications(List<ToolSpecification> toolSpecifications) {
            effectiveTools(toolSpecifications);
            availableTools(toolSpecifications);
            return this;
        }

        /**
         * Sets <b>all available</b> tool specifications configured for AI service.
         *
         * @see #effectiveTools(List)
         * @deprecated use {@link #toolServiceContext(ToolServiceContext)} instead
         */
        @Deprecated(since = "1.13.0")
        public Builder availableTools(List<ToolSpecification> availableTools) {
            ensureToolServiceContext();
            this.toolServiceContext = this.toolServiceContext.toBuilder()
                    .availableTools(availableTools)
                    .build();
            return this;
        }

        /**
         * Sets the tool executors.
         *
         * @deprecated use {@link #toolServiceContext(ToolServiceContext)} instead
         */
        @Deprecated(since = "1.13.0")
        public Builder toolExecutors(Map<String, ToolExecutor> toolExecutors) {
            ensureToolServiceContext();
            this.toolServiceContext = this.toolServiceContext.toBuilder()
                    .toolExecutors(toolExecutors)
                    .build();
            return this;
        }

        private void ensureToolServiceContext() {
            if (this.toolServiceContext == null) {
                this.toolServiceContext = ToolServiceContext.builder().build();
            }
        }

        /**
         * @since 1.4.0
         */
        public Builder toolArgumentsErrorHandler(ToolArgumentsErrorHandler handler) {
            this.toolArgumentsErrorHandler = handler;
            return this;
        }

        /**
         * @since 1.4.0
         */
        public Builder toolExecutionErrorHandler(ToolExecutionErrorHandler handler) {
            this.toolExecutionErrorHandler = handler;
            return this;
        }

        /**
         * @since 1.4.0
         */
        public Builder toolExecutor(Executor toolExecutor) {
            this.toolExecutor = toolExecutor;
            return this;
        }

        /**
         * Sets the executor that processes each complete response received from the {@code StreamingChatModel}:
         * updating the chat memory, executing the requested tools (or, when {@link #toolExecutor(Executor)} is set,
         * waiting for their results), sending the next request to the model, running the output guardrails,
         * firing the AI Service listener events and invoking
         * {@link TokenStream#onIntermediateResponse(java.util.function.Consumer)} and
         * {@link TokenStream#onCompleteResponse(java.util.function.Consumer)}.
         * Partial responses are still delivered on the thread of the {@code StreamingChatModel}.
         * <p>
         * If not set (or {@code null}), the response is processed on the thread that delivers it, which is usually
         * a thread of the HTTP client used by the {@code StreamingChatModel}.
         * Use this executor when that thread must not be blocked (e.g., an event loop thread)
         * and the processing can block (e.g., a tool, a chat memory store or an output guardrail doing I/O).
         * The executor can also decide per task whether to run it on the calling thread.
         * The context captured with {@link dev.langchain4j.spi.ExecutorProvider#captureContext()} when the AI Service
         * was invoked is restored on the executor's thread.
         * LangChain4j never shuts the executor down.
         * <p>
         * Anything thrown while processing the response, including an {@link Error}, is passed to
         * {@link TokenStream#onError(java.util.function.Consumer)}. So is an exception thrown by the executor itself
         * (e.g., {@link java.util.concurrent.RejectedExecutionException}), on the thread that delivered the response.
         * <p>
         * Ignored by {@link AiServiceStreamingEventPublisher}, which does not block the thread that delivers
         * the response.
         *
         * @since 1.23.0
         */
        public Builder completeResponseProcessingExecutor(Executor completeResponseProcessingExecutor) {
            this.completeResponseProcessingExecutor = completeResponseProcessingExecutor;
            return this;
        }

        /**
         * Sets the retrieved contents.
         *
         * @param retrievedContents the retrieved contents
         * @return this builder
         */
        public Builder retrievedContents(List<Content> retrievedContents) {
            this.retrievedContents = retrievedContents;
            return this;
        }

        /**
         * Sets the AI service context.
         *
         * @param context the AI service context
         * @return this builder
         */
        public Builder context(AiServiceContext context) {
            this.context = context;
            return this;
        }

        public Builder invocationContext(InvocationContext invocationContext) {
            this.invocationContext = invocationContext;
            return this;
        }

        /**
         * Sets the common guardrail parameters for validating interactions between a user and a language model.
         *
         * @param commonGuardrailParams an instance of {@link GuardrailRequestParams} containing the shared parameters
         *                              required for guardrail checks, such as chat memory, user message template,
         *                              and additional variables.
         * @return this builder instance.
         */
        public Builder commonGuardrailParams(GuardrailRequestParams commonGuardrailParams) {
            this.commonGuardrailParams = commonGuardrailParams;
            return this;
        }

        /**
         * Sets the method key.
         *
         * @param methodKey the method key
         * @return this builder
         */
        public Builder methodKey(Object methodKey) {
            this.methodKey = methodKey;
            return this;
        }

        /**
         * Builds a new {@link AiServiceTokenStreamParameters}.
         *
         * @return a new {@link AiServiceTokenStreamParameters}
         */
        public AiServiceTokenStreamParameters build() {
            return new AiServiceTokenStreamParameters(this);
        }
    }
}
