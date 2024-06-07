package dev.langchain4j.model.chat.listener;

import dev.langchain4j.Experimental;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import lombok.Builder;

/**
 * A response from the {@link ChatLanguageModel} or {@link StreamingChatLanguageModel},
 * intended to be used with {@link ChatModelListener}.
 */
@Experimental
public class ChatModelResponse {

    private final String id;
    private final String model;
    private final TokenUsage tokenUsage;
    private final FinishReason finishReason;
    private final AiMessage aiMessage;

    @Builder
    public ChatModelResponse(String id,
                             String model,
                             TokenUsage tokenUsage,
                             FinishReason finishReason,
                             AiMessage aiMessage) {
        this.id = id;
        this.model = model;
        this.tokenUsage = tokenUsage;
        this.finishReason = finishReason;
        this.aiMessage = aiMessage;
    }

    public String id() {
        return id;
    }

    public String model() {
        return model;
    }

    public TokenUsage tokenUsage() {
        return tokenUsage;
    }

    public FinishReason finishReason() {
        return finishReason;
    }

    public AiMessage aiMessage() {
        return aiMessage;
    }
}
