package dev.langchain4j.model.bedrock;

import dev.langchain4j.model.bedrock.internal.AbstractBedrockChatModel;
import java.util.HashMap;
import java.util.Map;

/**
 * @deprecated please use {@link BedrockChatModel} instead
 */
@Deprecated(forRemoval = true, since = "1.0.0-beta2")
public class BedrockCohereChatModel extends AbstractBedrockChatModel<BedrockCohereChatModelResponse> {

    public enum ReturnLikelihood {
        NONE,
        GENERATION,
        ALL
    }

    private static final int DEFAULT_TOP_K = 0;
    private static final String DEFAULT_MODEL = Types.CommandTextV14.getValue();

    private static ReturnLikelihood returnLikelihood = ReturnLikelihood.NONE;
    private final int topK;
    private final String model;

    @Override
    protected Map<String, Object> getRequestParameters(String prompt) {
        final Map<String, Object> parameters = new HashMap<>(7);

        parameters.put("prompt", prompt);
        parameters.put("max_tokens", getMaxTokens());
        parameters.put("temperature", getTemperature());
        parameters.put("p", getTopP());
        parameters.put("k", getTopK());
        parameters.put("stop_sequences", getStopSequences());
        parameters.put("return_likelihoods", returnLikelihood.name());

        return parameters;
    }

    @Override
    protected String getModelId() {
        return model;
    }

    @Override
    protected Class<BedrockCohereChatModelResponse> getResponseClassType() {
        return BedrockCohereChatModelResponse.class;
    }

    /**
     * Bedrock Cohere model ids
     */
    public enum Types {
        CommandTextV14("cohere.command-text-v14");

        private final String value;

        Types(String modelID) {
            this.value = modelID;
        }

        public String getValue() {
            return value;
        }
    }

    public static ReturnLikelihood getReturnLikelihood() {
        return returnLikelihood;
    }

    @Override
    public int getTopK() {
        return topK;
    }

    public String getModel() {
        return model;
    }

    protected BedrockCohereChatModel(BedrockCohereChatModelBuilder<?, ?> buider) {
        super(buider);
        if (buider.isTopKSet) {
            this.topK = buider.topK;
        } else {
            this.topK = DEFAULT_TOP_K;
        }
        if (buider.isModelSet) {
            this.model = buider.model;
        } else {
            this.model = DEFAULT_MODEL;
        }
    }

    public static BedrockCohereChatModelBuilder<?, ?> builder() {
        return new BedrockCohereChatModelBuilderImpl();
    }

    public abstract static class BedrockCohereChatModelBuilder<
                    C extends BedrockCohereChatModel, B extends BedrockCohereChatModelBuilder<C, B>>
            extends AbstractBedrockChatModel.AbstractBedrockChatModelBuilder<BedrockCohereChatModelResponse, C, B> {
        private boolean isTopKSet;
        private int topK;
        private boolean isModelSet;
        private String model;

        @Override
        public B topK(int topK) {
            this.topK = topK;
            this.isTopKSet = true;
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
            return "BedrockCohereChatModel.BedrockCohereChatModelBuilder(super=" + super.toString() + ", topK$value="
                    + this.topK + ", model$value=" + this.model + ")";
        }
    }

    private static final class BedrockCohereChatModelBuilderImpl
            extends BedrockCohereChatModelBuilder<BedrockCohereChatModel, BedrockCohereChatModelBuilderImpl> {
        protected BedrockCohereChatModelBuilderImpl self() {
            return this;
        }

        public BedrockCohereChatModel build() {
            return new BedrockCohereChatModel(this);
        }
    }
}
