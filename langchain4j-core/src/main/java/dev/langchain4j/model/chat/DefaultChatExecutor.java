package dev.langchain4j.model.chat;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.util.List;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.NullMarked;

/**
 * A concrete implementation of the {@link ChatExecutor} interface that executes
 * chat requests using a specified {@link ChatModel}.
 *
 * This class utilizes a {@link ChatRequest} to encapsulate the input messages
 * and parameters and delegates the execution of the chat to the provided
 * {@link ChatModel}.
 *
 * Instances of this class are immutable and are typically instantiated using
 * the {@link Builder}.
 */
@NullMarked
final class DefaultChatExecutor implements ChatExecutor {
    private final ChatRequest chatRequest;
    private final ChatModel chatModel;

    protected DefaultChatExecutor(Builder builder) {
        this.chatRequest = ensureNotNull(builder.chatRequest, "chatRequest");
        this.chatModel = ensureNotNull(builder.chatModel, "chatModel");
    }

    @Override
    public ChatResponse execute() {
        return execute(this.chatRequest);
    }

    @Override
    public ChatResponse execute(List<@NonNull ChatMessage> chatMessages) {
        var newChatRequest = this.chatRequest.toBuilder().messages(chatMessages).build();

        return execute(newChatRequest);
    }

    private ChatResponse execute(ChatRequest chatRequest) {
        return this.chatModel.chat(chatRequest);
    }
}
