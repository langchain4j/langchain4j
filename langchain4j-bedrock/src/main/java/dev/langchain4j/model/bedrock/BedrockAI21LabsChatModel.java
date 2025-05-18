package dev.langchain4j.model.bedrock;

import dev.langchain4j.model.bedrock.internal.AbstractBedrockChatModel;
import java.util.HashMap;
import java.util.Map;

/**
 * @deprecated please use {@link BedrockChatModel} instead
 */
@Deprecated(forRemoval = true, since = "1.0.0-beta2")
public class BedrockAI21LabsChatModel extends AbstractBedrockChatModel<BedrockAI21LabsChatModelResponse> {

    private static final String DEFAULT_MODEL = Types.J2MidV2.getValue();
    private static final Map<String, Object> DEFAULT_PENALTY = of("scale", 0);

    private final String model;
    private final Map<String, Object> countPenalty;
    private final Map<String, Object> presencePenalty;
    private final Map<String, Object> frequencyPenalty;

    @Override
    protected Map<String, Object> getRequestParameters(String prompt) {
        final Map<String, Object> parameters = new HashMap<>(8);

        parameters.put("prompt", prompt);
        parameters.put("maxTokens", getMaxTokens());
        parameters.put("temperature", getTemperature());
        parameters.put("topP", getTopP());
        parameters.put("stopSequences", getStopSequences());
        parameters.put("countPenalty", countPenalty);
        parameters.put("presencePenalty", presencePenalty);
        parameters.put("frequencyPenalty", frequencyPenalty);

        return parameters;
    }

    @Override
    protected String getModelId() {
        return model;
    }

    @Override
    protected Class<BedrockAI21LabsChatModelResponse> getResponseClassType() {
        return BedrockAI21LabsChatModelResponse.class;
    }

    /**
     * Bedrock AI21 Labs model ids
     */
    public enum Types {
        J2MidV2("ai21.j2-mid-v1"),
        J2UltraV1("ai21.j2-ultra-v1");

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

    public Map<String, Object> getCountPenalty() {
        return countPenalty;
    }

    public Map<String, Object> getPresencePenalty() {
        return presencePenalty;
    }

    public Map<String, Object> getFrequencyPenalty() {
        return frequencyPenalty;
    }

    protected BedrockAI21LabsChatModel(BedrockAI21LabsChatModelBuilder<?, ?> builder) {
        super(builder);
        if (builder.isModelSet) {
            this.model = builder.model;
        } else {
            this.model = DEFAULT_MODEL;
        }

        if (builder.isCountPenaltySet) {
            this.countPenalty = builder.countPenalty;
        } else {
            this.countPenalty = DEFAULT_PENALTY;
        }

        if (builder.isPresencePenaltySet) {
            this.presencePenalty = builder.presencePenalty;
        } else {
            this.presencePenalty = DEFAULT_PENALTY;
        }

        if (builder.isFrequencyPenaltySet) {
            this.frequencyPenalty = builder.frequencyPenalty;
        } else {
            this.frequencyPenalty = DEFAULT_PENALTY;
        }
    }

    public static BedrockAI21LabsChatModelBuilder<?, ?> builder() {
        return new BedrockAI21LabsChatModelBuilderImpl();
    }

    public abstract static class BedrockAI21LabsChatModelBuilder<
                    C extends BedrockAI21LabsChatModel, B extends BedrockAI21LabsChatModelBuilder<C, B>>
            extends AbstractBedrockChatModel.AbstractBedrockChatModelBuilder<BedrockAI21LabsChatModelResponse, C, B> {
        private boolean isModelSet;
        private String model;
        private boolean isCountPenaltySet;
        private Map<String, Object> countPenalty;
        private boolean isPresencePenaltySet;
        private Map<String, Object> presencePenalty;
        private boolean isFrequencyPenaltySet;
        private Map<String, Object> frequencyPenalty;

        public B model(String model) {
            this.model = model;
            this.isModelSet = true;
            return self();
        }

        public B countPenalty(Map<String, Object> countPenalty) {
            this.countPenalty = countPenalty;
            this.isCountPenaltySet = true;
            return self();
        }

        public B presencePenalty(Map<String, Object> presencePenalty) {
            this.presencePenalty = presencePenalty;
            this.isPresencePenaltySet = true;
            return self();
        }

        public B frequencyPenalty(Map<String, Object> frequencyPenalty) {
            this.frequencyPenalty = frequencyPenalty;
            this.isFrequencyPenaltySet = true;
            return self();
        }

        protected abstract B self();

        public abstract C build();

        @Override
        public String toString() {
            return "BedrockAI21LabsChatModel.BedrockAI21LabsChatModelBuilder(super=" + super.toString()
                    + ", model$value=" + this.model + ", countPenalty$value=" + this.countPenalty
                    + ", presencePenalty$value=" + this.presencePenalty + ", frequencyPenalty$value="
                    + this.frequencyPenalty + ")";
        }
    }

    private static final class BedrockAI21LabsChatModelBuilderImpl
            extends BedrockAI21LabsChatModelBuilder<BedrockAI21LabsChatModel, BedrockAI21LabsChatModelBuilderImpl> {
        protected BedrockAI21LabsChatModelBuilderImpl self() {
            return this;
        }

        public BedrockAI21LabsChatModel build() {
            return new BedrockAI21LabsChatModel(this);
        }
    }
}
