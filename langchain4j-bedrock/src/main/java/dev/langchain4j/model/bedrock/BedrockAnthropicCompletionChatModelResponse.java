package dev.langchain4j.model.bedrock;

import dev.langchain4j.model.bedrock.internal.BedrockChatModelResponse;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

/**
 * Bedrock Anthropic Text Completions API Invoke response
 * <a href="https://docs.aws.amazon.com/bedrock/latest/userguide/model-parameters-anthropic-claude-text-completion.html">...</a>
 */
@Getter
@Setter
public class BedrockAnthropicCompletionChatModelResponse implements BedrockChatModelResponse {
    
    private String completion;
    private String stop_reason;
    
    @Getter
    @Setter
    static class BedrockAnthropicContent {
        private String type;
        private String text;
        private BedrockAnthropicImageSource source;
        
        public BedrockAnthropicContent(String type, String text) {
            this.type = type;
            this.text = text;
        }
        
        public BedrockAnthropicContent(String type, BedrockAnthropicImageSource source) {
            this.type = type;
            this.source = source;
        }
    }
   
    @Getter
    @Setter
    @AllArgsConstructor
    static class BedrockAnthropicImageSource {
        private String type;
        private String media_type;
        private String data;
    }
   
    @Getter
    @Setter
    @AllArgsConstructor
    static class BedrockAnthropicMessage {
        private String role;
        private List<BedrockAnthropicContent> content;
    }

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
