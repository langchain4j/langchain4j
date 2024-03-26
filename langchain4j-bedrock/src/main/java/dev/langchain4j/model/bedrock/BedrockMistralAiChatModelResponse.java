package dev.langchain4j.model.bedrock;

import dev.langchain4j.model.bedrock.internal.BedrockChatModelResponse;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * Bedrock Mistral AI Invoke response
 */
@Getter
@Setter
class BedrockMistralAiChatModelResponse implements BedrockChatModelResponse {
    
    private List<Output> outputs;
    
    @Getter
    @Setter
    public static class Output {
        private String text;
        private String stop_reason;
    }

    @Override
    public String getOutputText() {
        return outputs.get(0).text;
    }

    @Override
    public FinishReason getFinishReason() {
        String stop_reason = outputs.get(0).stop_reason;
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
        return null;
    }
}
