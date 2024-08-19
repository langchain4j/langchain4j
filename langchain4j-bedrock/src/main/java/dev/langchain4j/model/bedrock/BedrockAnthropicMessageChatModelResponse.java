package dev.langchain4j.model.bedrock;

import dev.langchain4j.model.bedrock.internal.BedrockChatModelResponse;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import java.util.List;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.Setter;

/**
 * Bedrock Anthropic Messages API Invoke response
 * <a href="https://docs.aws.amazon.com/bedrock/latest/userguide/model-parameters-anthropic-claude-messages.html">...</a>
 */
@Getter
@Setter
public class BedrockAnthropicMessageChatModelResponse implements BedrockChatModelResponse {
    
    private String id;
    private String model;
    private String type;
    private String role;
    private List<BedrockAnthropicContent> content;
    private String stop_reason;
    private String stop_sequence;
    private BedrockAnthropicUsage usage;
    
    @Getter
    @Setter
    public static class BedrockAnthropicUsage {
        private int input_tokens;
        private int output_tokens;
    }

    @Override
    public String getOutputText() {
        return content.stream().map(BedrockAnthropicContent::getText).collect(Collectors.joining("\n\n"));
    }

    @Override
    public FinishReason getFinishReason() {
        switch (stop_reason) {
        case "end_turn":
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
      if (usage != null) {
          int totalTokenCount = usage.input_tokens + usage.output_tokens;
          return new TokenUsage(usage.input_tokens, usage.output_tokens, totalTokenCount);
      } else {
          return null;
      }
    }
}
