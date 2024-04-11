package dev.langchain4j.model.bedrock;

import dev.langchain4j.model.bedrock.internal.AbstractBedrockChatModel;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

import java.util.HashMap;
import java.util.Map;

@Getter
@SuperBuilder
public class BedrockAI21LabsChatModel extends AbstractBedrockChatModel<BedrockAI21LabsChatModelResponse> {

    @Builder.Default
    private final String model = Types.J2MidV2.getValue();
    @Builder.Default
    private final Map<String, Object> countPenalty = of("scale", 0);
    @Builder.Default
    private final Map<String, Object> presencePenalty = of("scale", 0);
    @Builder.Default
    private final Map<String, Object> frequencyPenalty = of("scale", 0);


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
    @Getter
    public enum Types {
        J2MidV2("ai21.j2-mid-v1"),
        J2UltraV1("ai21.j2-ultra-v1");

        private final String value;

        Types(String modelID) {
            this.value = modelID;
        }
    }
}
