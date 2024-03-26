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

    @Builder.Default
    private final String model = Types.MetaLlama2Chat70B.getValue();

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
        MetaLlama2Chat13B("meta.llama2-13b-chat-v1"),
        MetaLlama2Chat70B("meta.llama2-70b-chat-v1");

        private final String value;

        Types(String modelID) {
            this.value = modelID;
        }

    }
}
