package dev.langchain4j.model.googleai;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
record GeminiGenerationConfig(
        @JsonProperty("stopSequences") List<String> stopSequences,
        @JsonProperty("responseMimeType") String responseMimeType,
        @JsonProperty("responseSchema") GeminiSchema responseSchema,
        @JsonProperty("responseJsonSchema") Map<String, Object> responseJsonSchema,
        @JsonProperty("candidateCount") Integer candidateCount,
        @JsonProperty("maxOutputTokens") Integer maxOutputTokens,
        @JsonProperty("temperature") Double temperature,
        @JsonProperty("topK") Integer topK,
        @JsonProperty("seed") Integer seed,
        @JsonProperty("topP") Double topP,
        @JsonProperty("presencePenalty") Double presencePenalty,
        @JsonProperty("frequencyPenalty") Double frequencyPenalty,
        @JsonProperty("thinkingConfig") GeminiThinkingConfig thinkingConfig,
        @JsonProperty("responseLogprobs") Boolean responseLogprobs,
        @JsonProperty("enableEnhancedCivicAnswers") Boolean enableEnhancedCivicAnswers,
        @JsonProperty("logprobs") Integer logprobs,
        @JsonProperty("mediaResolution") GeminiMediaResolutionLevel mediaResolution) {

    static GeminiGenerationConfigBuilder builder() {
        return new GeminiGenerationConfigBuilder();
    }

    static class GeminiGenerationConfigBuilder {

        private List<String> stopSequences;
        private String responseMimeType;
        private GeminiSchema responseSchema;
        private Map<String, Object> responseJsonSchema;
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
        private GeminiMediaResolutionLevel mediaResolution;

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

        GeminiGenerationConfigBuilder responseJsonSchema(Map<String, Object> responseJsonSchema) {
            this.responseJsonSchema = responseJsonSchema;
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

        GeminiGenerationConfigBuilder responseLogprobs(Boolean responseLogprobs) {
            this.responseLogprobs = responseLogprobs;
            return this;
        }

        GeminiGenerationConfigBuilder enableEnhancedCivicAnswers(Boolean enableEnhancedCivicAnswers) {
            this.enableEnhancedCivicAnswers = enableEnhancedCivicAnswers;
            return this;
        }

        GeminiGenerationConfigBuilder logprobs(Integer logprobs) {
            this.logprobs = logprobs;
            return this;
        }

        GeminiGenerationConfigBuilder mediaResolution(GeminiMediaResolutionLevel mediaResolution) {
            this.mediaResolution = mediaResolution;
            return this;
        }

        GeminiGenerationConfig build() {
            return new GeminiGenerationConfig(
                    stopSequences,
                    responseMimeType,
                    responseSchema,
                    responseJsonSchema,
                    candidateCount,
                    maxOutputTokens,
                    temperature,
                    topK,
                    seed,
                    topP,
                    presencePenalty,
                    frequencyPenalty,
                    thinkingConfig,
                    responseLogprobs,
                    enableEnhancedCivicAnswers,
                    logprobs,
                    mediaResolution);
        }
    }
}
