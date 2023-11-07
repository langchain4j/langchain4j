package dev.langchain4j.model.bedrock;

import dev.langchain4j.model.bedrock.internal.BedrockChatInstance;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import lombok.Getter;
import lombok.Setter;

/**
 * Bedrock Anthropic Invoke response
 */
@Getter
@Setter
public class BedrockAnthropicChatResponse implements BedrockChatInstance {
    private String completion;
    private String stop_reason;

    @Override
    public String getOutputText() {
        return completion;
    }

    @Override
    public FinishReason getFinishReason() {
        switch (stop_reason) {
            case "stop_sequence":
                return FinishReason.STOP;
            case "max_tokens":
                return FinishReason.LENGTH;
            default:
                throw new IllegalArgumentException("Unknown stop reason: " + stop_reason);
        }
    }

    @Override
    public TokenUsage getTokenUsage() {
        return null;
    }
}
