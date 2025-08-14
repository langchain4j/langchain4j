package dev.langchain4j.guardrail;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.Internal;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;

/**
 * A concrete implementation of the {@link ChatExecutor} interface that executes
 * chat requests using a specified {@link ChatModel}.
 *
 * This class utilizes a {@link ChatRequest} to encapsulate the input messages
 * and parameters and delegates the execution of the chat to the provided
 * {@link ChatModel}.
 *
 * Instances of this class are immutable and are typically instantiated using
 * the {@link SynchronousBuilder}.
 */
@Internal
final class SynchronousChatExecutor extends AbstractChatExecutor {
    private final ChatModel chatModel;

    protected SynchronousChatExecutor(SynchronousBuilder builder) {
        super(builder);
        this.chatModel = ensureNotNull(builder.chatModel, "chatModel");
    }

    @Override
    protected ChatResponse execute(ChatRequest chatRequest) {
        return this.chatModel.chat(chatRequest);
    }
}
