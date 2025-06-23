package dev.langchain4j.guardrail;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.Internal;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.util.List;

/**
 * Abstract base class for chat executors that provides a common structure and shared functionality
 * for implementing the {@link ChatExecutor} interface.
 *
 * This class encapsulates a {@link ChatRequest} and allows subclasses to define how
 * the request should be processed by implementing the {@code execute(ChatRequest)} method.
 *
 * Subclasses are expected to be immutable and should provide specific implementations for
 * executing chat requests, typically using particular chat models or processing strategies.
 *
 * Responsibilities:
 * - Stores a {@link ChatRequest} object which can be used to build specific chat requests.
 * - Provides standard implementations for executing a chat request with a list of messages
 *   or without any additional input.
 * - Defines an abstract method {@code execute(ChatRequest)} for subclasses to implement
 *   specific execution logic.
 */
@Internal
abstract class AbstractChatExecutor implements ChatExecutor {
    protected final ChatRequest chatRequest;

    protected AbstractChatExecutor(AbstractBuilder<?> builder) {
        this.chatRequest = ensureNotNull(builder.chatRequest, "chatRequest");
    }

    @Override
    public ChatResponse execute(List<ChatMessage> chatMessages) {
        var newChatRequest = this.chatRequest.toBuilder().messages(chatMessages).build();

        return execute(newChatRequest);
    }

    @Override
    public ChatResponse execute() {
        return execute(this.chatRequest);
    }

    /**
     * Executes a given chat request and returns the corresponding chat response.
     *
     * @param chatRequest the chat request to process, containing the input messages and any necessary configurations
     * @return the chat response generated as a result of processing the given chat request
     */
    protected abstract ChatResponse execute(ChatRequest chatRequest);
}
