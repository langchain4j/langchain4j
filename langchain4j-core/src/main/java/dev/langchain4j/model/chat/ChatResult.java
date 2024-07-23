package dev.langchain4j.model.chat;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import lombok.Builder;

import java.util.Map;

/**
 * TODO
 */
public class ChatResult {
    // TODO name (ChatModelResponse?)

    private final AiMessage aiMessage;
    private final String id; // TODO name, type
    private final TokenUsage tokenUsage;
    private final FinishReason finishReason;
    private final Map<String, Object> otherMetadata; // TODO name, type, remove?

    @Builder
    private ChatResult(AiMessage aiMessage,
                       String id,
                       TokenUsage tokenUsage,
                       FinishReason finishReason,
                       Map<String, Object> otherMetadata) {
        // TODO
        this.aiMessage = aiMessage;
        this.id = id;
        this.tokenUsage = tokenUsage;
        this.finishReason = finishReason;
        this.otherMetadata = otherMetadata;
    }

    public AiMessage aiMessage() {
        return aiMessage;
    }

    public String id() {
        return id;
    }

    public TokenUsage tokenUsage() {
        return tokenUsage;
    }

    public FinishReason finishReason() {
        return finishReason;
    }

    public Map<String, Object> otherMetadata() {
        return otherMetadata;
    }
}
