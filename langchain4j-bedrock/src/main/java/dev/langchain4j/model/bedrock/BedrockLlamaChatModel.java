package dev.langchain4j.model.bedrock;

import dev.langchain4j.model.bedrock.internal.AbstractBedrockChatModel;
import dev.langchain4j.model.bedrock.internal.BedrockChatModelResponse;
import java.util.HashMap;
import java.util.Map;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
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
    @Getter
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

    }
}
