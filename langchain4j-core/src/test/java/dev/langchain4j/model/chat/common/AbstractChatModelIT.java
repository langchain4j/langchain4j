package dev.langchain4j.model.chat.common;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.junit.jupiter.api.TestInstance;

import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

/**
 * Contains all the common tests that every {@link ChatLanguageModel} must successfully pass.
 * This ensures that {@link ChatLanguageModel} implementations are interchangeable among themselves.
 */
@TestInstance(PER_CLASS)
public abstract class AbstractChatModelIT extends AbstractBaseChatModelIT<ChatLanguageModel> {

    @Override
    protected ChatResponseAndStreamingMetadata chat(ChatLanguageModel chatModel, ChatRequest chatRequest) {
        ChatResponse chatResponse = chatModel.chat(chatRequest);
        return new ChatResponseAndStreamingMetadata(chatResponse, null);
    }
}
