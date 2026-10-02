package dev.langchain4j.guardrail;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.Internal;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.util.concurrent.CompletableFuture;

/**
 * A concrete implementation of the {@link ChatExecutor} interface that executes chat requests by delegating
 * directly to a {@link ChatModel} - synchronously via {@link ChatModel#chat(ChatRequest)} and asynchronously via
 * {@link ChatModel#chatAsync(ChatRequest)}. (The streaming counterpart is {@link StreamingToSynchronousChatExecutor}.)
 *
 * <p>Instances of this class are immutable and are typically instantiated using the {@link SynchronousBuilder}.
 */
@Internal
final class DirectChatExecutor extends AbstractChatExecutor {
    private final ChatModel chatModel;

    protected DirectChatExecutor(SynchronousBuilder builder) {
        super(builder);
        this.chatModel = ensureNotNull(builder.chatModel, "chatModel");
    }

    @Override
    protected ChatResponse execute(ChatRequest chatRequest) {
        return this.chatModel.chat(chatRequest);
    }

    @Override
    protected CompletableFuture<ChatResponse> executeAsync(ChatRequest chatRequest) {
        return this.chatModel.chatAsync(chatRequest);
    }
}
