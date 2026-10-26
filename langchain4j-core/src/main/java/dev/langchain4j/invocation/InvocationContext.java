package dev.langchain4j.invocation;

import dev.langchain4j.Experimental;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.spi.CapturedContext;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jspecify.annotations.NonNull;

/**
 * Represents the context of a single AI Service invocation.
 * A new instance is created each time an AI Service method is invoked,
 * and it exists until the end of the AI Service invocation,
 * potentially spanning multiple calls to the {@link ChatModel}.
 *
 * @since 1.6.0
 */
public interface InvocationContext {

    /**
     * Unique identifier for an entire AI Service invocation. AI Services created with
     * {@code AiServices} treat each subscription to a publisher returned by an AI Service
     * method as a separate invocation, with its own identifier.
     */
    UUID invocationId();

    /**
     * The {@link #invocationId()} of the parent invocation: the AI Service invocation whose own work started this
     * invocation. This is the case when one of these parts of an AI Service calls another AI Service:
     * <ul>
     * <li>a tool, a tool error handler or the strategy for hallucinated tool names</li>
     * <li>a tool provider</li>
     * <li>an input or output guardrail</li>
     * <li>a component of the retrieval augmentor (for example a query transformer or a content retriever)</li>
     * <li>the system or user message provider, or the system message transformer</li>
     * <li>the moderation model</li>
     * <li>the {@code chatRequestTransformer}</li>
     * </ul>
     * <p>
     * It is {@code null} when the AI Service is called from your own code (for example from a handler of a
     * {@code TokenStream} or from a listener, also from those of an AI Service that itself has a parent), from any
     * other part of an AI Service (for example its chat memory), when there is no parent, or when this
     * {@code InvocationContext} implementation does not track it.
     * <p>
     * The parent is also known when LangChain4j itself runs that part on another thread (for example tools executed
     * concurrently, or a blocking content retriever offloaded to an executor). But if your own code in that part hands
     * the call to another thread (for example with {@code CompletableFuture.supplyAsync(..., executor)}), it is
     * {@code null}. In rare cases, when futures are shared between invocations, it can even name another
     * invocation. So treat it as best effort.
     * <p>
     * It is filled by the AI Service implementation that LangChain4j provides. Frameworks that replace that
     * implementation (for example through {@code AiServicesFactory}) return {@code null} until they add support for
     * it, also for AI Services they create through {@code AiServices.builder(...)}.
     * <p>
     * It is set when the AI Service method is called: later runs of the same {@code TokenStream} keep it.
     * <p>
     * Agents run by an agentic workflow (sequence, parallel, loop, conditional) or by a supervisor currently have no
     * parent. An agent called from a tool of another agent has one.
     *
     * @return the id of the parent invocation, or {@code null} if there is none or it is not known
     * @since 1.23.0
     */
    @Experimental
    default UUID parentInvocationId() {
        return null;
    }

    /**
     * The fully-qualified name of the AI Service interface where the invocation was initiated from
     *
     * @see #methodName()
     */
    String interfaceName();

    /**
     * The method name on {@link #interfaceName()} where the invocation was initiated from
     */
    String methodName();

    /**
     * The arguments passed into the AI Service method
     */
    List<Object> methodArguments();

    /**
     * The {@link UserMessage} to be sent to the LLM.
     * This is the message after all transformations (RAG augmentation,
     * content injection, input guardrails, output format instructions).
     *
     * @since 1.13.0
     */
    default UserMessage userMessage() {
        return null;
    }

    /**
     * The {@link UserMessage} created from the AI Service method and its prompt template, before any transformation
     * (RAG augmentation, content injection, input guardrails, output format instructions).
     * It is what the user asked, without the retrieved content, for example to decide which tools or models are
     * relevant to the question.
     *
     * @since 1.21.0
     */
    default UserMessage originalUserMessage() {
        return null;
    }

    /**
     * The chat memory id parameter of the method
     */
    Object chatMemoryId();

    /**
     * Returns the default {@link ChatRequestParameters} of the chat model
     * configured on the AI service.
     *
     * @since 1.16.0
     */
    default ChatRequestParameters defaultRequestParameters() {
        return null;
    }

    /**
     * Returns the {@link ModelProvider} of the chat model
     * configured on the AI service.
     *
     * @since 1.16.0
     */
    default ModelProvider modelProvider() {
        return null;
    }

    /**
     * The invocation parameters
     */
    InvocationParameters invocationParameters();

    /**
     * LangChain4j managed parameters
     * @since 1.8.0
     */
    default Map<Class<? extends LangChain4jManaged>, LangChain4jManaged> managedParameters() {
        return Map.of();
    }

    /**
     * Retrieves the point in time when the invocation occurred.
     */
    Instant timestamp();

    /**
     * Converts the current instance of {@link DefaultInvocationContext} into a {@link Builder},
     * pre-populated with the current values of the instance.
     * <p>
     * When this is a {@link DefaultInvocationContext}, a copy built with the same {@link #invocationId()} shares
     * whether the invocation has already reported its end (see {@link InvocationEnd}).
     */
    default Builder toBuilder() {
        return new Builder(this);
    }

    /**
     * Creates a new instance of the {@code Builder} class for constructing
     * instances of {@link DefaultInvocationContext}.
     */
    static Builder builder() {
        return new Builder();
    }

    /**
     * A builder class to create {@link InvocationContext} instances.
     */
    class Builder {

        private UUID invocationId;
        private UUID parentInvocationId;
        private String interfaceName;
        private String methodName;
        private List<@NonNull Object> methodArguments = new ArrayList<>();
        private UserMessage userMessage;
        private UserMessage originalUserMessage;
        private Object chatMemoryId;
        private ChatRequestParameters defaultRequestParameters;
        private ModelProvider modelProvider;
        private InvocationParameters invocationParameters;
        private Map<Class<? extends LangChain4jManaged>, LangChain4jManaged> managedParameters;
        private Instant timestamp;
        CapturedContext capturedContext;
        AtomicBoolean ended;
        UUID endedInvocationId;

        protected Builder() {}

        protected Builder(InvocationContext invocationContext) {
            invocationId(invocationContext.invocationId());
            parentInvocationId(invocationContext.parentInvocationId());
            interfaceName(invocationContext.interfaceName());
            methodName(invocationContext.methodName());
            methodArguments(invocationContext.methodArguments());
            userMessage(invocationContext.userMessage());
            originalUserMessage(invocationContext.originalUserMessage());
            chatMemoryId(invocationContext.chatMemoryId());
            defaultRequestParameters(invocationContext.defaultRequestParameters());
            modelProvider(invocationContext.modelProvider());
            invocationParameters(invocationContext.invocationParameters());
            managedParameters(invocationContext.managedParameters());
            timestamp(invocationContext.timestamp());
            if (invocationContext instanceof DefaultInvocationContext defaultInvocationContext) {
                this.capturedContext = defaultInvocationContext.capturedContext();
                this.ended = defaultInvocationContext.ended();
                this.endedInvocationId = defaultInvocationContext.invocationId();
            }
        }

        /**
         * Sets the invocation ID.
         */
        public Builder invocationId(UUID invocationId) {
            this.invocationId = invocationId;
            return this;
        }

        /**
         * Sets the {@link InvocationContext#parentInvocationId()}.
         *
         * @since 1.23.0
         */
        @Experimental
        public Builder parentInvocationId(UUID parentInvocationId) {
            this.parentInvocationId = parentInvocationId;
            return this;
        }

        /**
         * Sets the name of the interface associated with the builder.
         */
        public Builder interfaceName(String interfaceName) {
            this.interfaceName = interfaceName;
            return this;
        }

        /**
         * Sets the name of the method associated with the builder.
         */
        public Builder methodName(String methodName) {
            this.methodName = methodName;
            return this;
        }

        /**
         * Sets the method arguments for the builder. If the provided list of method arguments is not null,
         * they will be added to the existing list of method arguments.
         */
        public Builder methodArguments(List<Object> methodArguments) {
            if (methodArguments != null) {
                this.methodArguments.addAll(methodArguments);
            }

            return this;
        }

        /**
         * Adds a single method argument to the builder's list of method arguments.
         * If the provided argument is not null, it will be added to the collection.
         */
        public Builder methodArgument(Object methodArgument) {
            if (methodArgument != null) {
                this.methodArguments.add(methodArgument);
            }

            return this;
        }

        /**
         * Sets the final user message that was sent to the LLM.
         */
        public Builder userMessage(UserMessage userMessage) {
            this.userMessage = userMessage;
            return this;
        }

        /**
         * Sets the user message before any transformation, see {@link InvocationContext#originalUserMessage()}.
         *
         * @since 1.21.0
         */
        public Builder originalUserMessage(UserMessage originalUserMessage) {
            this.originalUserMessage = originalUserMessage;
            return this;
        }

        /**
         * Sets the memory identifier for the builder.
         */
        public Builder chatMemoryId(Object memoryId) {
            this.chatMemoryId = memoryId;
            return this;
        }

        /**
         * Sets the default request parameters for the builder.
         */
        public Builder defaultRequestParameters(ChatRequestParameters defaultRequestParameters) {
            this.defaultRequestParameters = defaultRequestParameters;
            return this;
        }

        /**
         * Sets the model provider for the builder.
         */
        public Builder modelProvider(ModelProvider modelProvider) {
            this.modelProvider = modelProvider;
            return this;
        }

        /**
         * Sets the invocation parameters for the builder.
         */
        public Builder invocationParameters(InvocationParameters invocationParameters) {
            this.invocationParameters = invocationParameters;
            return this;
        }

        /**
         * Sets the LC4j managed parameters for the builder.
         */
        public Builder managedParameters(Map<Class<? extends LangChain4jManaged>, LangChain4jManaged> managedParameters) {
            this.managedParameters = managedParameters;
            return this;
        }

        /**
         * Sets the timestamp for the builder.
         */
        public Builder timestamp(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        /**
         * Updates the builder's timestamp to the current system time.
         *
         * @return This Builder instance with the timestamp set to the current time.
         */
        public Builder timestampNow() {
            return timestamp(Instant.now());
        }

        /**
         * Constructs an instance of {@link InvocationContext} using the current state of the builder.
         */
        public <T extends InvocationContext> T build() {
            return (T) new DefaultInvocationContext(this);
        }

        public UUID invocationId() {
            return invocationId;
        }

        @Experimental
        public UUID parentInvocationId() {
            return parentInvocationId;
        }

        public String interfaceName() {
            return interfaceName;
        }

        public String methodName() {
            return methodName;
        }

        public List<@NonNull Object> methodArguments() {
            return methodArguments;
        }

        public UserMessage userMessage() {
            return userMessage;
        }

        public UserMessage originalUserMessage() {
            return originalUserMessage;
        }

        public Object chatMemoryId() {
            return chatMemoryId;
        }

        public ChatRequestParameters defaultRequestParameters() {
            return defaultRequestParameters;
        }

        public ModelProvider modelProvider() {
            return modelProvider;
        }

        public InvocationParameters invocationParameters() {
            return invocationParameters;
        }

        public Map<Class<? extends LangChain4jManaged>, LangChain4jManaged> managedParameters() {
            return managedParameters;
        }

        public Instant timestamp() {
            return timestamp;
        }
    }
}
