package dev.langchain4j.model.chat;

import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Generic executor interface that defines a chat interaction
 */
@NullMarked
public interface ChatExecutor {
    /**
     * Execute a chat request
     * @return The response
     */
    ChatResponse execute();

    /**
     * Executes a chat request using the provided chat memory.
     *
     *
     * @param chatMemory The chat memory containing the context of the conversation.
     *                   It provides the history of messages required for proper interaction with the chat language model.
     * @return A response object containing the AI's response and additional metadata.
     */
    ChatResponse execute(@Nullable ChatMemory chatMemory);

    /**
     * Creates a new {@link Builder} instance for constructing {@link ChatExecutor} objects
     * that perform synchronous chat requests.
     *
     * @return A new {@link Builder} instance to configure and build a {@link ChatExecutor}.
     */
    static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for constructing instances of {@link ChatExecutor}.
     *
     * This builder provides a fluent API for setting required components
     * like {@link ChatRequest}, and for building an instance of the {@link ChatExecutor}.
     */
    class Builder {
        protected ChatRequest chatRequest;
        protected ChatLanguageModel chatLanguageModel;

        protected Builder() {}

        /**
         * Sets the {@link ChatRequest} instance for the synchronousBuilder.
         * The {@link ChatRequest} encapsulates the input messages and parameters required
         * to generate a response from the chat model.
         *
         * @param chatRequest the {@link ChatRequest} containing the input messages and parameters
         * @return the updated Builder instance
         */
        public Builder chatRequest(ChatRequest chatRequest) {
            this.chatRequest = chatRequest;
            return this;
        }

        /**
         * Sets the {@link ChatLanguageModel} instance for the Builder.
         * The {@link ChatLanguageModel} represents a language model that provides a chat API.
         *
         * @param chatLanguageModel the {@link ChatLanguageModel} to be used by the Builder
         * @return the updated Builder instance
         */
        public Builder chatLanguageModel(ChatLanguageModel chatLanguageModel) {
            this.chatLanguageModel = chatLanguageModel;
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
            return new DefaultChatExecutor(this);
        }
    }
}
