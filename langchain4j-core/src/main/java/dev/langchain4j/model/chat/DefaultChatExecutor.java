package dev.langchain4j.model.chat;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.util.ArrayList;
import java.util.Optional;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * A concrete implementation of the {@link ChatExecutor} interface that executes
 * chat requests using a specified {@link ChatLanguageModel}.
 *
 * This class utilizes a {@link ChatRequest} to encapsulate the input messages
 * and parameters and delegates the execution of the chat to the provided
 * {@link ChatLanguageModel}.
 *
 * Instances of this class are immutable and are typically instantiated using
 * the {@link Builder}.
 */
@NullMarked
final class DefaultChatExecutor implements ChatExecutor {
    private final ChatRequest chatRequest;
    private final ChatLanguageModel chatLanguageModel;

    protected DefaultChatExecutor(Builder builder) {
        this.chatRequest = ensureNotNull(builder.chatRequest, "chatRequest");
        this.chatLanguageModel = ensureNotNull(builder.chatLanguageModel, "chatLanguageModel");
    }

    @Override
    public ChatResponse execute() {
        return execute(this.chatRequest);
    }

    @Override
    public ChatResponse execute(@Nullable ChatMemory chatMemory) {
        var messages = Optional.ofNullable(chatMemory).map(ChatMemory::messages).orElseGet(ArrayList::new);

        var newChatRequest = this.chatRequest.toBuilder().messages(messages).build();
        return execute(newChatRequest);
    }

    private ChatResponse execute(ChatRequest chatRequest) {
        return this.chatLanguageModel.chat(chatRequest);
    }
}
