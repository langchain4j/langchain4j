package dev.langchain4j.model.bedrock;

import dev.langchain4j.model.bedrock.internal.AbstractBedrockChatModel;
import java.util.HashMap;
import java.util.Map;

/**
 * @deprecated please use {@link BedrockChatModel} instead
 */
@Deprecated(forRemoval = true, since = "1.0.0-beta2")
public class BedrockTitanChatModel extends AbstractBedrockChatModel<BedrockTitanChatModelResponse> {

    private static final String DEFAULT_MODEL = Types.TitanTextExpressV1.getValue();

    private final String model;

    @Override
    protected Map<String, Object> getRequestParameters(String prompt) {

        final Map<String, Object> textGenerationConfig = new HashMap<>(4);
        textGenerationConfig.put("maxTokenCount", getMaxTokens());
        textGenerationConfig.put("temperature", getTemperature());
        textGenerationConfig.put("topP", getTopP());
        textGenerationConfig.put("stopSequences", getStopSequences());

        final Map<String, Object> parameters = new HashMap<>(2);
        parameters.put("inputText", prompt);
        parameters.put("textGenerationConfig", textGenerationConfig);

        return parameters;
    }

    @Override
    protected String getModelId() {
        return model;
    }

    @Override
    public Class<BedrockTitanChatModelResponse> getResponseClassType() {
        return BedrockTitanChatModelResponse.class;
    }

    /**
     * Bedrock Amazon Titan model ids
     */
    public enum Types {
        TitanTg1Large("amazon.titan-tg1-large"),
        TitanTextExpressV1("amazon.titan-text-express-v1");

        private final String value;

        Types(String modelID) {
            this.value = modelID;
        }

        public String getValue() {
            return value;
        }
    }

    public String getModel() {
        return model;
    }

    protected BedrockTitanChatModel(BedrockTitanChatModelBuilder<?, ?> builder) {
        super(builder);
        if (builder.isModelSet) {
            this.model = builder.model;
        } else {
            this.model = DEFAULT_MODEL;
        }
    }

    public static BedrockTitanChatModelBuilder<?, ?> builder() {
        return new BedrockTitanChatModelBuilderImpl();
    }

    public abstract static class BedrockTitanChatModelBuilder<
                    C extends BedrockTitanChatModel, B extends BedrockTitanChatModelBuilder<C, B>>
            extends AbstractBedrockChatModel.AbstractBedrockChatModelBuilder<BedrockTitanChatModelResponse, C, B> {
        private String model;
        private boolean isModelSet;

        public B model(String model) {
            this.model = model;
            this.isModelSet = true;
            return self();
        }

        protected abstract B self();

        public abstract C build();

        @Override
        public String toString() {
            return "BedrockTitanChatModel.BedrockTitanChatModelBuilder(super=" + super.toString() + ", model$value="
                    + this.model + ")";
        }
    }

    private static final class BedrockTitanChatModelBuilderImpl
            extends BedrockTitanChatModelBuilder<BedrockTitanChatModel, BedrockTitanChatModelBuilderImpl> {
        protected BedrockTitanChatModelBuilderImpl self() {
            return this;
        }

        public BedrockTitanChatModel build() {
            return new BedrockTitanChatModel(this);
        }
    }
}
