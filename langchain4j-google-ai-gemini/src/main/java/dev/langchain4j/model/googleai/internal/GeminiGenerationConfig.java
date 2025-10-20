package dev.langchain4j.model.googleai.internal;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import dev.langchain4j.Internal;
import dev.langchain4j.model.googleai.GeminiThinkingConfig;
import java.util.List;

@Internal
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class GeminiGenerationConfig {

    @JsonProperty("stopSequences")
    private final List<String> stopSequences;

    @JsonProperty("responseMimeType")
    private final String responseMimeType;

    @JsonProperty("responseSchema")
    private final GeminiSchema responseSchema;

    @JsonProperty("candidateCount")
    private final Integer candidateCount;

    @JsonProperty("maxOutputTokens")
    private final Integer maxOutputTokens;

    @JsonProperty("temperature")
    private final Double temperature;

    @JsonProperty("topK")
    private final Integer topK;

    @JsonProperty("seed")
    private Integer seed;

    @JsonProperty("topP")
    private final Double topP;

    @JsonProperty("presencePenalty")
    private final Double presencePenalty;

    @JsonProperty("frequencyPenalty")
    private final Double frequencyPenalty;

    @JsonProperty("thinkingConfig")
    private final GeminiThinkingConfig thinkingConfig;

    @JsonProperty("responseLogprobs")
    private final Boolean responseLogprobs;

    @JsonProperty("enableEnhancedCivicAnswers")
    private final Boolean enableEnhancedCivicAnswers;

    @JsonProperty("logprobs")
    private final Integer logprobs;

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
        this.responseLogprobs = builder.responseLogprobs;
        this.enableEnhancedCivicAnswers = builder.enableEnhancedCivicAnswers;
        this.thinkingConfig = builder.thinkingConfig;
        this.logprobs = builder.logprobs;
    }

    public static GeminiGenerationConfigBuilder builder() {
        return new GeminiGenerationConfigBuilder();
    }

    @Internal
    public static class GeminiGenerationConfigBuilder {

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
        private Boolean responseLogprobs;
        private Boolean enableEnhancedCivicAnswers;
        private GeminiThinkingConfig thinkingConfig;
        private Integer logprobs;

        GeminiGenerationConfigBuilder() {}

        public GeminiGenerationConfigBuilder stopSequences(List<String> stopSequences) {
            this.stopSequences = stopSequences;
            return this;
        }

        public GeminiGenerationConfigBuilder responseMimeType(String responseMimeType) {
            this.responseMimeType = responseMimeType;
            return this;
        }

        public GeminiGenerationConfigBuilder responseSchema(GeminiSchema responseSchema) {
            this.responseSchema = responseSchema;
            return this;
        }

        public GeminiGenerationConfigBuilder candidateCount(Integer candidateCount) {
            this.candidateCount = candidateCount;
            return this;
        }

        public GeminiGenerationConfigBuilder maxOutputTokens(Integer maxOutputTokens) {
            this.maxOutputTokens = maxOutputTokens;
            return this;
        }

        public GeminiGenerationConfigBuilder temperature(Double temperature) {
            this.temperature = temperature;
            return this;
        }

        public GeminiGenerationConfigBuilder topK(Integer topK) {
            this.topK = topK;
            return this;
        }

        public GeminiGenerationConfigBuilder seed(Integer seed) {
            this.seed = seed;
            return this;
        }

        public GeminiGenerationConfigBuilder topP(Double topP) {
            this.topP = topP;
            return this;
        }

        public GeminiGenerationConfigBuilder presencePenalty(Double presencePenalty) {
            this.presencePenalty = presencePenalty;
            return this;
        }

        public GeminiGenerationConfigBuilder frequencyPenalty(Double frequencyPenalty) {
            this.frequencyPenalty = frequencyPenalty;
            return this;
        }

        public GeminiGenerationConfigBuilder thinkingConfig(GeminiThinkingConfig thinkingConfig) {
            this.thinkingConfig = thinkingConfig;
            return this;
        }

        public GeminiGenerationConfigBuilder responseLogprobs(Boolean responseLogprobs) {
            this.responseLogprobs = responseLogprobs;
            return this;
        }

        public GeminiGenerationConfigBuilder enableEnhancedCivicAnswers(Boolean enableEnhancedCivicAnswers) {
            this.enableEnhancedCivicAnswers = enableEnhancedCivicAnswers;
            return this;
        }

        public GeminiGenerationConfigBuilder logprobs(Integer logprobs) {
            this.logprobs = logprobs;
            return this;
        }

        public GeminiGenerationConfig build() {
            return new GeminiGenerationConfig(this);
        }
    }
}
