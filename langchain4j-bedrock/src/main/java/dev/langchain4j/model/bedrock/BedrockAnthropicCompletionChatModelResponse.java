package dev.langchain4j.model.bedrock;

import dev.langchain4j.model.bedrock.internal.BedrockChatModelResponse;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import lombok.Getter;
import lombok.Setter;

/**
 * @deprecated please use {@link BedrockChatModel}
 */
@Deprecated(forRemoval = true, since = "1.0.0-beta2")
@Getter
@Setter
public class BedrockAnthropicCompletionChatModelResponse implements BedrockChatModelResponse {
    
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
