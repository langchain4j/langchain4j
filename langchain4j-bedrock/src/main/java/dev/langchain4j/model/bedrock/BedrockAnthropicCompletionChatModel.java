package dev.langchain4j.model.bedrock;

import dev.langchain4j.model.bedrock.internal.AbstractBedrockChatModel;
import java.util.HashMap;
import java.util.Map;

/**
 * @deprecated please use {@link BedrockChatModel} instead
 */
@Deprecated(forRemoval = true, since = "1.0.0-beta2")
public class BedrockAnthropicCompletionChatModel
        extends AbstractBedrockChatModel<BedrockAnthropicCompletionChatModelResponse> {

    private static final String DEFAULT_ANTHROPIC_VERSION = "bedrock-2023-05-31";
    private static final int DEFAULT_TOP_K = 250;
    private static final String DEFAULT_MODEL = Types.AnthropicClaudeV2.getValue();

    private final int topK;
    private final String anthropicVersion;
    private final String model;

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

        public String getValue() {
            return value;
        }
    }

    protected BedrockAnthropicCompletionChatModel(BedrockAnthropicCompletionChatModelBuilder<?, ?> builder) {
        super(builder);
        if (builder.isTopKSet) {
            this.topK = builder.topK;
        } else {
            this.topK = DEFAULT_TOP_K;
        }

        if (builder.isAnthropicVersionSet) {
            this.anthropicVersion = builder.anthropicVersion;
        } else {
            this.anthropicVersion = DEFAULT_ANTHROPIC_VERSION;
        }

        if (builder.isModelSet) {
            this.model = builder.model;
        } else {
            this.model = DEFAULT_MODEL;
        }
    }

    public static BedrockAnthropicCompletionChatModelBuilder<?, ?> builder() {
        return new BedrockAnthropicCompletionChatModelBuilderImpl();
    }

    public abstract static class BedrockAnthropicCompletionChatModelBuilder<
                    C extends BedrockAnthropicCompletionChatModel,
                    B extends BedrockAnthropicCompletionChatModelBuilder<C, B>>
            extends AbstractBedrockChatModel.AbstractBedrockChatModelBuilder<
                    BedrockAnthropicCompletionChatModelResponse, C, B> {
        private boolean isTopKSet;
        private int topK;
        private boolean isAnthropicVersionSet;
        private String anthropicVersion;
        private boolean isModelSet;
        private String model;

        @Override
        public B topK(int topK) {
            this.topK = topK;
            this.isTopKSet = true;
            return self();
        }

        @Override
        public B anthropicVersion(String anthropicVersion) {
            this.anthropicVersion = anthropicVersion;
            this.isAnthropicVersionSet = true;
            return self();
        }

        public B model(String model) {
            this.model = model;
            this.isModelSet = true;
            return self();
        }

        protected abstract B self();

        public abstract C build();

        @Override
        public String toString() {
            return "BedrockAnthropicCompletionChatModel.BedrockAnthropicCompletionChatModelBuilder(super="
                    + super.toString() + ", topK$value=" + this.topK + ", anthropicVersion$value="
                    + this.anthropicVersion + ", model$value=" + this.model + ")";
        }
    }

    private static final class BedrockAnthropicCompletionChatModelBuilderImpl
            extends BedrockAnthropicCompletionChatModelBuilder<
                    BedrockAnthropicCompletionChatModel, BedrockAnthropicCompletionChatModelBuilderImpl> {
        protected BedrockAnthropicCompletionChatModelBuilderImpl self() {
            return this;
        }

        public BedrockAnthropicCompletionChatModel build() {
            return new BedrockAnthropicCompletionChatModel(this);
        }
    }
}
