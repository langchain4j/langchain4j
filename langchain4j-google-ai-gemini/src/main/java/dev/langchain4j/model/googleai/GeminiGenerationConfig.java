package dev.langchain4j.model.googleai;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
record GeminiGenerationConfig(
        List<String> stopSequences,
        String responseMimeType,
        GeminiSchema responseSchema,
        Integer candidateCount,
        Integer maxOutputTokens,
        Double temperature,
        Integer topK,
        Integer seed,
        Double topP,
        Double presencePenalty,
        Double frequencyPenalty,
        GeminiThinkingConfig thinkingConfig,
        Boolean responseLogprobs,
        Integer logprobs) {

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
        private Boolean responseLogprobs;
        private GeminiThinkingConfig thinkingConfig;
        private Integer logprobs;

        GeminiGenerationConfigBuilder() {
        }

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

        GeminiGenerationConfigBuilder responseLogprobs(Boolean responseLogprobs) {
            this.responseLogprobs = responseLogprobs;
            return this;
        }

        GeminiGenerationConfigBuilder logprobs(Integer logprobs) {
            this.logprobs = logprobs;
            return this;
        }

        GeminiGenerationConfig build() {
            return new GeminiGenerationConfig(
                    stopSequences,
                    responseMimeType,
                    responseSchema,
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
                    logprobs);
        }
    }
}
