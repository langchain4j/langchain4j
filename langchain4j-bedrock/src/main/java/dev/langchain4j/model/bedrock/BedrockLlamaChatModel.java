package dev.langchain4j.model.bedrock;

import dev.langchain4j.model.bedrock.internal.AbstractBedrockChatModel;
import java.util.HashMap;
import java.util.Map;

/**
 * @deprecated please use {@link BedrockChatModel} instead
 */
@Deprecated(forRemoval = true, since = "1.0.0-beta2")
public class BedrockLlamaChatModel extends AbstractBedrockChatModel<BedrockLlamaChatModelResponse> {

    private final String model;

    @Override
    protected String getModelId() {
        return model;
    }

    @Override
    protected Map<String, Object> getRequestParameters(String prompt) {
        final Map<String, Object> parameters = new HashMap<>(7);

        parameters.put("prompt", prompt);
        parameters.put("max_gen_len", getMaxTokens());
        parameters.put("temperature", getTemperature());
        parameters.put("top_p", getTopP());

        return parameters;
    }

    @Override
    public Class<BedrockLlamaChatModelResponse> getResponseClassType() {
        return BedrockLlamaChatModelResponse.class;
    }

    /**
     * Bedrock Llama model ids
     */
    public enum Types {
        META_LLAMA3_2_1B_INSTRUCT_V1_0("meta.llama3-2-1b-instruct-v1:0"),
        META_LLAMA3_2_3B_INSTRUCT_V1_0("meta.llama3-2-3b-instruct-v1:0"),
        META_LLAMA3_2_11B_INSTRUCT_V1_0("meta.llama3-2-11b-instruct-v1:0"),
        META_LLAMA3_2_90B_INSTRUCT_V1_0("meta.llama3-2-90b-instruct-v1:0"),

        META_LLAMA3_1_70B_INSTRUCT_V1_0("meta.llama3-1-70b-instruct-v1:0"),
        META_LLAMA3_1_8B_INSTRUCT_V1_0("meta.llama3-1-8b-instruct-v1:0"),

        META_LLAMA3_8B_INSTRUCT_V1_0("meta.llama3-8b-instruct-v1:0"),
        META_LLAMA3_70B_INSTRUCT_V1_0("meta.llama3-70b-instruct-v1:0");

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

    protected BedrockLlamaChatModel(BedrockLlamaChatModelBuilder<?, ?> b) {
        super(b);
        this.model = b.model;
    }

    public static BedrockLlamaChatModelBuilder<?, ?> builder() {
        return new BedrockLlamaChatModelBuilderImpl();
    }

    public abstract static class BedrockLlamaChatModelBuilder<
                    C extends BedrockLlamaChatModel, B extends BedrockLlamaChatModelBuilder<C, B>>
            extends AbstractBedrockChatModel.AbstractBedrockChatModelBuilder<BedrockLlamaChatModelResponse, C, B> {
        private String model;

        public B model(String model) {
            this.model = model;
            return self();
        }

        protected abstract B self();

        public abstract C build();

        @Override
        public String toString() {
            return "BedrockLlamaChatModel.BedrockLlamaChatModelBuilder(super=" + super.toString() + ", model="
                    + this.model + ")";
        }
    }

    private static final class BedrockLlamaChatModelBuilderImpl
            extends BedrockLlamaChatModelBuilder<BedrockLlamaChatModel, BedrockLlamaChatModelBuilderImpl> {
        protected BedrockLlamaChatModelBuilderImpl self() {
            return this;
        }

        public BedrockLlamaChatModel build() {
            return new BedrockLlamaChatModel(this);
        }
    }
}
