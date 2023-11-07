package dev.langchain4j.model.bedrock;

import dev.langchain4j.model.bedrock.internal.BedrockChatModel;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

import java.util.HashMap;
import java.util.Map;

@Getter
@SuperBuilder
public class BedrockCohereChatModel extends BedrockChatModel<BedrockCohereChatResponse> {

    public enum ReturnLikelihood {
        NONE,
        GENERATION,
        ALL
    }

    @Builder.Default
    private static ReturnLikelihood returnLikelihood = ReturnLikelihood.NONE;
    @Builder.Default
    private final int topK = 0;
    @Builder.Default
    private final Types model = Types.CommandTextV14;

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
        return model.getValue();
    }

    @Override
    protected Class<BedrockCohereChatResponse> getResponseClassType() {
        return BedrockCohereChatResponse.class;
    }

    /**
     * Bedrock Cohere model ids
     */
    @Getter
    public enum Types {
        CommandTextV14("cohere.command-text-v14");

        private final String value;

        Types(String modelID) {
            this.value = modelID;
        }

    }
}
