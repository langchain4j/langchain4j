package dev.langchain4j.model.bedrock;

import dev.langchain4j.model.bedrock.internal.AbstractBedrockStreamingChatModel;

/**
 * @deprecated please use {@link BedrockStreamingChatModel} instead
 */
@Deprecated(forRemoval = true, since = "1.0.0-beta4")
public class BedrockAnthropicStreamingChatModel extends AbstractBedrockStreamingChatModel {

    private static String DEFAULT_MODEL = BedrockAnthropicStreamingChatModel.Types.AnthropicClaudeV2.getValue();

    private final String model;

    protected BedrockAnthropicStreamingChatModel(BedrockAnthropicStreamingChatModelBuilder<?, ?> builder) {
        super(builder);
        if (builder.isModelSet) {
            this.model = builder.model;
        } else {
            this.model = DEFAULT_MODEL;
        }
    }

    @Override
    protected String getModelId() {
        return model;
    }

    public String getModel() {
        return model;
    }

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

        public String getValue() {
            return value;
        }
    }

    public abstract static class BedrockAnthropicStreamingChatModelBuilder<
                    C extends BedrockAnthropicStreamingChatModel,
                    B extends BedrockAnthropicStreamingChatModelBuilder<C, B>>
            extends AbstractBedrockStreamingChatModel.AbstractBedrockStreamingChatModelBuilder<C, B> {
        private boolean isModelSet;
        private String model;

        public B model(String model) {
            this.model = model;
            this.isModelSet = true;
            return self();
        }

        protected abstract B self();

        public abstract C build();

        public String toString() {
            return "BedrockAnthropicStreamingChatModel.BedrockAnthropicStreamingChatModelBuilder(super="
                    + super.toString() + ", model$value=" + model + ")";
        }
    }

    public static BedrockAnthropicStreamingChatModelBuilder<?, ?> builder() {
        return new BedrockAnthropicStreamingChatModelBuilderImpl();
    }

    private static final class BedrockAnthropicStreamingChatModelBuilderImpl
            extends BedrockAnthropicStreamingChatModelBuilder<
                    BedrockAnthropicStreamingChatModel, BedrockAnthropicStreamingChatModelBuilderImpl> {
        public BedrockAnthropicStreamingChatModelBuilderImpl self() {
            return this;
        }

        public BedrockAnthropicStreamingChatModel build() {
            return new BedrockAnthropicStreamingChatModel(this);
        }
    }
}
