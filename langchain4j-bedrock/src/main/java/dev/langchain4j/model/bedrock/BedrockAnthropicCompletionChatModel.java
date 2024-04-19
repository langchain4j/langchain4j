package dev.langchain4j.model.bedrock;

import dev.langchain4j.model.bedrock.internal.AbstractBedrockChatModel;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

import java.util.HashMap;
import java.util.Map;

@Getter
@SuperBuilder
public class BedrockAnthropicCompletionChatModel extends AbstractBedrockChatModel<BedrockAnthropicCompletionChatModelResponse> {
    
    private static final String DEFAULT_ANTHROPIC_VERSION = "bedrock-2023-05-31";

    @Builder.Default
    private final int topK = 250;
    @Builder.Default
    private final String anthropicVersion = DEFAULT_ANTHROPIC_VERSION;
    @Builder.Default
    private final String model = Types.AnthropicClaudeV2.getValue();

    @Override
    protected String getModelId() {
        return model;
    }

    @Override
    protected Map<String, Object> getRequestParameters(String prompt) {
        final Map<String, Object> parameters = new HashMap<>(7);

        parameters.put("prompt", prompt);
        parameters.put("max_tokens_to_sample", getMaxTokens());
        parameters.put("temperature", getTemperature());
        parameters.put("top_k", topK);
        parameters.put("top_p", getTopP());
        parameters.put("stop_sequences", getStopSequences());
        parameters.put("anthropic_version", anthropicVersion);

        return parameters;
    }

    @Override
    public Class<BedrockAnthropicCompletionChatModelResponse> getResponseClassType() {
        return BedrockAnthropicCompletionChatModelResponse.class;
    }

    /**
     * Bedrock Anthropic model ids
     */
    @Getter
    public enum Types {
        AnthropicClaudeInstantV1("anthropic.claude-instant-v1"),
        AnthropicClaudeV1("anthropic.claude-v1"),
        AnthropicClaudeV2("anthropic.claude-v2"),
        AnthropicClaudeV2_1("anthropic.claude-v2:1"),
        AnthropicClaude3SonnetV1("anthropic.claude-3-sonnet-20240229-v1:0");

        private final String value;

        Types(String modelID) {
            this.value = modelID;
        }
    }
}
