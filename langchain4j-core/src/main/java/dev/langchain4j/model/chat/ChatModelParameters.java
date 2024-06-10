package dev.langchain4j.model.chat;

import lombok.Builder;

import java.util.Collection;
import java.util.Map;

/**
 * TODO
 */
public class ChatModelParameters {
    // TODO name

    private final String modelName; // TODO name (model?)
    private final Integer maxTokens; // TODO name
    private final Double temperature;
    private final Double topP;
    private final Collection<String> stopSequences; // TODO name, type
    private final Integer seed; // TODO name, type
    private final ResponseFormat responseFormat; // TODO name, type
    private final Map<String, Object> otherParameters; // TODO name, type

    @Builder
    private ChatModelParameters(String modelName,
                                Integer maxTokens,
                                Double temperature,
                                Double topP,
                                Collection<String> stopSequences,
                                Integer seed,
                                ResponseFormat responseFormat,
                                Map<String, Object> otherParameters) {
        this.modelName = modelName;
        this.maxTokens = maxTokens;
        this.temperature = temperature;
        this.topP = topP;
        this.stopSequences = stopSequences; // TODO copy
        this.seed = seed;
        this.responseFormat = responseFormat;
        this.otherParameters = otherParameters; // TODO copy
    }

    // TODO getters
    // TODO default ctor? setters?
}
