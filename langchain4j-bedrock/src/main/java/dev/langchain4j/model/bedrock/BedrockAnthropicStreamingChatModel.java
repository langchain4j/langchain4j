package dev.langchain4j.model.bedrock;

import dev.langchain4j.model.bedrock.internal.AbstractBedrockStreamingChatModel;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
public class BedrockAnthropicStreamingChatModel extends AbstractBedrockStreamingChatModel {
    @Builder.Default
    private final String model = BedrockAnthropicStreamingChatModel.Types.AnthropicClaudeV2.getValue();

    @Override
    protected String getModelId() {
        return model;
    }

    @Getter
    /**
     * Bedrock Anthropic model ids
     */
    public enum Types {
        AnthropicClaudeV2("anthropic.claude-v2"),
        AnthropicClaudeV2_1("anthropic.claude-v2:1");

        private final String value;

        Types(String modelID) {
            this.value = modelID;
        }
    }
}
