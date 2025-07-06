package dev.langchain4j.guardrail;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.util.List;
import java.util.function.Consumer;

/**
 * Generic executor interface that defines a chat interaction
 */
public interface ChatExecutor {

    /**
     * Execute a chat request
     * @return The response
     */
    ChatResponse execute();

    /**
     * Executes a chat request using the provided chat messages
     * @param chatMessages The chat messages containing the context of the conversation.
     *                     It provides the history of messages required for proper interaction with the chat model
     * @return A response object containing the AI's response and additional metadata.
     */
    ChatResponse execute(List<ChatMessage> chatMessages);

    /**
     * Creates a new {@link SynchronousBuilder} instance for constructing {@link ChatExecutor} objects
     * that perform synchronous chat requests.
     *
     * @return A new {@link SynchronousBuilder} instance to configure and build a {@link ChatExecutor}.
     */
    static SynchronousBuilder builder(ChatModel chatModel) {
        return new SynchronousBuilder(chatModel);
    }

    /**
     * Creates a new {@link StreamingToSynchronousBuilder} instance for constructing {@link ChatExecutor} objects
     * that perform streaming chat requests.
     *
     * @return A new {@link StreamingToSynchronousBuilder} instance to configure and build a {@link ChatExecutor}.
     */
    static StreamingToSynchronousBuilder builder(StreamingChatModel streamingChatModel) {
        return new StreamingToSynchronousBuilder(streamingChatModel);
    }

    /**
     * An abstract base-builder class for constructing instances of {@link ChatExecutor}.
     *
     * This class provides a fluent API for setting required components, such as
     * {@link ChatRequest}, and defines a contract for building {@link ChatExecutor}
     * instances. Subclasses should implement the {@code build()} method to ensure
     * proper construction of the target chat executor object.
     *
     * @param <T> the type of the builder subclass for enabling fluent method chaining
     */
    abstract class AbstractBuilder<T extends AbstractBuilder<T>> {
        protected ChatRequest chatRequest;

        protected AbstractBuilder() {}

        /**
         * Sets the {@link ChatRequest} instance for the synchronousBuilder.
         * The {@link ChatRequest} encapsulates the input messages and parameters required
         * to generate a response from the chat model.
         *
         * @param chatRequest the {@link ChatRequest} containing the input messages and parameters
         * @return the updated SynchronousBuilder instance
         */
        public AbstractBuilder<T> chatRequest(ChatRequest chatRequest) {
            this.chatRequest = chatRequest;
            return this;
        }

        /**
         * Constructs and returns an instance of {@link ChatExecutor}.
         * Ensures that all required parameters have been appropriately set
         * before building the {@link ChatExecutor}.
         *
         * @return a fully constructed {@link ChatExecutor} instance
         */
        public abstract ChatExecutor build();
    }

    /**
     * SynchronousBuilder for constructing instances of {@link ChatExecutor}.
     *
     * This synchronousBuilder provides a fluent API for setting required components
     * like {@link ChatRequest}, and for building an instance of the {@link ChatExecutor}.
     */
    class SynchronousBuilder extends AbstractBuilder<SynchronousBuilder> {
        protected final ChatModel chatModel;

        protected SynchronousBuilder(ChatModel chatModel) {
            this.chatModel = ensureNotNull(chatModel, "chatModel");
        }

        /**
         * Constructs and returns an instance of {@link ChatExecutor}.
         * Ensures that all required parameters have been appropriately set
         * before building the {@link ChatExecutor}.
         *
         * @return a fully constructed {@link ChatExecutor} instance
         */
        public ChatExecutor build() {
            return new SynchronousChatExecutor(this);
        }
    }

    /**
     * StreamingToSynchronousBuilder for constructing instances of {@link ChatExecutor}.
     *
     * This streaming build provides a fluent API for setting required components
     * like {@link ChatRequest}, and for building an instance of the {@link ChatExecutor}
     * that simulates streaming.
     */
    class StreamingToSynchronousBuilder extends AbstractBuilder<StreamingToSynchronousBuilder> {
        protected final StreamingChatModel streamingChatModel;
        protected Consumer<Throwable> errorHandler;

        protected StreamingToSynchronousBuilder(StreamingChatModel streamingChatModel) {
            this.streamingChatModel = ensureNotNull(streamingChatModel, "streamingChatModel");
        }

        /**
         * Sets a custom error handler to manage exceptions or errors that occur during the execution.
         *
         * @param errorHandler a {@link Consumer} of {@link Throwable} that processes the error
         * @return the current {@link StreamingToSynchronousBuilder} instance for method chaining
         */
        public StreamingToSynchronousBuilder errorHandler(Consumer<Throwable> errorHandler) {
            this.errorHandler = errorHandler;
            return this;
        }

        /**
         * Constructs and returns an instance of {@link ChatExecutor}.
         * Ensures that all required parameters have been appropriately set
         * before building the {@link ChatExecutor}.
         *
         * @return a fully constructed {@link ChatExecutor} instance
         */
        public ChatExecutor build() {
            return new StreamingToSynchronousChatExecutor(this);
        }
    }
}
