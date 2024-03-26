package dev.langchain4j.model.bedrock;

import dev.langchain4j.model.bedrock.internal.BedrockChatModelResponse;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import lombok.Getter;
import lombok.Setter;

/**
 * Bedrock Llama Invoke response
 */
@Getter
@Setter
public class BedrockLlamaChatModelResponse implements BedrockChatModelResponse {
    
    private String generation;
    private int prompt_token_count;
    private int generation_token_count;
    private String stop_reason;

    @Override
    public String getOutputText() {
        return generation;
    }

    @Override
    public FinishReason getFinishReason() {
        switch (stop_reason) {
            case "stop":
                return FinishReason.STOP;
            case "length":
                return FinishReason.LENGTH;
            default:
                throw new IllegalArgumentException("Unknown stop reason: " + stop_reason);
        }
    }

    @Override
    public TokenUsage getTokenUsage() {
        return new TokenUsage(prompt_token_count, generation_token_count);
    }
}
