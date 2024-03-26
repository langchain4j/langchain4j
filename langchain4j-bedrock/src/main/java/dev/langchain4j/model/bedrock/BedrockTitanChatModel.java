package dev.langchain4j.model.bedrock;

import dev.langchain4j.model.bedrock.internal.AbstractBedrockChatModel;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

import java.util.HashMap;
import java.util.Map;

/**
 * Bedrock Amazon Titan chat model
 */
@Getter
@SuperBuilder
public class BedrockTitanChatModel extends AbstractBedrockChatModel<BedrockTitanChatModelResponse> {

    @Builder.Default
    private final String model = Types.TitanTextExpressV1.getValue();

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
    @Getter
    public enum Types {
        TitanTg1Large("amazon.titan-tg1-large"),
        TitanTextExpressV1("amazon.titan-text-express-v1");

        private final String value;

        Types(String modelID) {
            this.value = modelID;
        }
    }
}
