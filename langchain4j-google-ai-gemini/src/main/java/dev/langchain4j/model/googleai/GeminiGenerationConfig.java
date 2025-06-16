package dev.langchain4j.model.googleai;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
class GeminiGenerationConfig {

    @JsonProperty
    private final List<String> stopSequences;

    @JsonProperty
    private final String responseMimeType;

    @JsonProperty
    private final GeminiSchema responseSchema;

    @JsonProperty
    private final Integer candidateCount;

    @JsonProperty
    private final Integer maxOutputTokens;

    @JsonProperty
    private final Double temperature;

    @JsonProperty
    private final Integer topK;

    @JsonProperty
    private Integer seed;

    @JsonProperty
    private final Double topP;

    @JsonProperty
    private final Double presencePenalty;

    @JsonProperty
    private final Double frequencyPenalty;

    @JsonProperty
    private final GeminiThinkingConfig thinkingConfig;

    GeminiGenerationConfig(GeminiGenerationConfigBuilder builder) {
        this.stopSequences = builder.stopSequences;
        this.responseMimeType = builder.responseMimeType;
        this.responseSchema = builder.responseSchema;
        this.candidateCount = builder.candidateCount;
        this.maxOutputTokens = builder.maxOutputTokens;
        this.temperature = builder.temperature;
        this.topK = builder.topK;
        this.seed = builder.seed;
        this.topP = builder.topP;
        this.presencePenalty = builder.presencePenalty;
        this.frequencyPenalty = builder.frequencyPenalty;
        this.thinkingConfig = builder.thinkingConfig;
    }

    static GeminiGenerationConfigBuilder builder() {
        return new GeminiGenerationConfigBuilder();
    }

    static class GeminiGenerationConfigBuilder {

        private List<String> stopSequences;
        private String responseMimeType;
        private GeminiSchema responseSchema;
        private Integer candidateCount;
        private Integer maxOutputTokens;
        private Double temperature;
        private Integer topK;
        private Integer seed;
        private Double topP;
        private Double presencePenalty;
        private Double frequencyPenalty;
        private GeminiThinkingConfig thinkingConfig;

        GeminiGenerationConfigBuilder() {}

        GeminiGenerationConfigBuilder stopSequences(List<String> stopSequences) {
            this.stopSequences = stopSequences;
            return this;
        }

        GeminiGenerationConfigBuilder responseMimeType(String responseMimeType) {
            this.responseMimeType = responseMimeType;
            return this;
        }

        GeminiGenerationConfigBuilder responseSchema(GeminiSchema responseSchema) {
            this.responseSchema = responseSchema;
            return this;
        }

        GeminiGenerationConfigBuilder candidateCount(Integer candidateCount) {
            this.candidateCount = candidateCount;
            return this;
        }

        GeminiGenerationConfigBuilder maxOutputTokens(Integer maxOutputTokens) {
            this.maxOutputTokens = maxOutputTokens;
            return this;
        }

        GeminiGenerationConfigBuilder temperature(Double temperature) {
            this.temperature = temperature;
            return this;
        }

        GeminiGenerationConfigBuilder topK(Integer topK) {
            this.topK = topK;
            return this;
        }

        GeminiGenerationConfigBuilder seed(Integer seed) {
            this.seed = seed;
            return this;
        }

        GeminiGenerationConfigBuilder topP(Double topP) {
            this.topP = topP;
            return this;
        }

        GeminiGenerationConfigBuilder presencePenalty(Double presencePenalty) {
            this.presencePenalty = presencePenalty;
            return this;
        }

        GeminiGenerationConfigBuilder frequencyPenalty(Double frequencyPenalty) {
            this.frequencyPenalty = frequencyPenalty;
            return this;
        }

        GeminiGenerationConfigBuilder thinkingConfig(GeminiThinkingConfig thinkingConfig) {
            this.thinkingConfig = thinkingConfig;
            return this;
        }

        GeminiGenerationConfig build() {
            return new GeminiGenerationConfig(this);
        }
    }
}
