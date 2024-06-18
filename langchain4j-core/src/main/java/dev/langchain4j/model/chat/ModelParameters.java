package dev.langchain4j.model.chat;

import lombok.Builder;

import java.util.List;
import java.util.Map;

import static dev.langchain4j.internal.Utils.copyIfNotNull;

/**
 * TODO
 */
public class ModelParameters {
    // TODO name: ChatModelParameters?
    // TODO move to model package?

    private final String modelName; // TODO name (model?)
    private final Integer maxTokens; // TODO name
    private final Double temperature;
    private final Double topP;
    private final List<String> stopSequences; // TODO name, type
    private final Integer seed; // TODO name, type
    private final ResponseFormat responseFormat; // TODO name, type
    private final Map<String, Object> otherParameters; // TODO name, type, remove?

    @Builder
    private ModelParameters(String modelName,
                            Integer maxTokens,
                            Double temperature,
                            Double topP,
                            List<String> stopSequences,
                            Integer seed,
                            ResponseFormat responseFormat,
                            Map<String, Object> otherParameters) {
        this.modelName = modelName;
        this.maxTokens = maxTokens;
        this.temperature = temperature;
        this.topP = topP;
        this.stopSequences = copyIfNotNull(stopSequences);
        this.seed = seed;
        this.responseFormat = responseFormat;
        this.otherParameters = copyIfNotNull(otherParameters);
    }

    public String modelName() {
        return modelName;
    }

    public Integer maxTokens() {
        return maxTokens;
    }

    public Double temperature() {
        return temperature;
    }

    public Double topP() {
        return topP;
    }

    public List<String> stopSequences() {
        return stopSequences;
    }

    public Integer seed() {
        return seed;
    }

    public ResponseFormat responseFormat() {
        return responseFormat;
    }

    public Map<String, Object> otherParameters() {
        return otherParameters;
    }

    // TODO default ctor? setters?
}
