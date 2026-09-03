package dev.langchain4j.model.googleai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
record GeminiGenerateContentResponse(
        @JsonProperty("responseId") String responseId,
        @JsonProperty("modelVersion") String modelVersion,
        @JsonProperty("candidates") List<GeminiCandidate> candidates,
        @JsonProperty("usageMetadata") GeminiUsageMetadata usageMetadata,
        @JsonProperty("groundingMetadata") GroundingMetadata groundingMetadata,
        @JsonProperty("promptFeedback") GeminiPromptFeedback promptFeedback) {

    GeminiGenerateContentResponse(
            String responseId,
            String modelVersion,
            List<GeminiCandidate> candidates,
            GeminiUsageMetadata usageMetadata,
            GroundingMetadata groundingMetadata) {
        this(responseId, modelVersion, candidates, usageMetadata, groundingMetadata, null);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record GeminiCandidate(
            @JsonProperty("content") GeminiContent content,
            @JsonProperty("finishReason") GeminiFinishReason finishReason,
            @JsonProperty("urlContextMetadata") GeminiUrlContextMetadata urlContextMetadata,
            @JsonProperty("groundingMetadata") GroundingMetadata groundingMetadata,
            @JsonProperty("safetyRatings") List<GeminiSafetyRating> safetyRatings) {

        GeminiCandidate(
                GeminiContent content,
                GeminiFinishReason finishReason,
                GeminiUrlContextMetadata urlContextMetadata,
                GroundingMetadata groundingMetadata) {
            this(content, finishReason, urlContextMetadata, groundingMetadata, null);
        }

        enum GeminiFinishReason {
            FINISH_REASON_UNSPECIFIED,
            STOP,
            MAX_TOKENS,
            SAFETY,
            RECITATION,
            LANGUAGE,
            OTHER,
            BLOCKLIST,
            PROHIBITED_CONTENT,
            SPII,
            MALFORMED_FUNCTION_CALL,
            IMAGE_RECITATION,
            IMAGE_SAFETY,
            IMAGE_PROHIBITED_CONTENT,
            IMAGE_OTHER,
            NO_IMAGE,
            UNEXPECTED_TOOL_CALL,
            TOO_MANY_TOOL_CALLS,
            MISSING_THOUGHT_SIGNATURE,
            MALFORMED_RESPONSE,
            ESCALATION
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record GeminiPromptFeedback(
            @JsonProperty("blockReason") String blockReason,
            @JsonProperty("safetyRatings") List<GeminiSafetyRating> safetyRatings) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record GeminiUrlContextMetadata(
            @JsonProperty("urlMetadata") List<GeminiUrlMetadata> urlMetadata) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record GeminiUrlMetadata(
            @JsonProperty("retrievedUrl") String retrievedUrl,
            @JsonProperty("urlRetrievalStatus") GeminiUrlRetrievalStatus urlRetrievalStatus) {}

    enum GeminiUrlRetrievalStatus {
        URL_RETRIEVAL_STATUS_UNSPECIFIED,
        URL_RETRIEVAL_STATUS_SUCCESS,
        URL_RETRIEVAL_STATUS_ERROR,
        URL_RETRIEVAL_STATUS_PAYWALL,
        URL_RETRIEVAL_STATUS_UNSAFE
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record GeminiUsageMetadata(
            @JsonProperty("promptTokenCount") Integer promptTokenCount,
            @JsonProperty("candidatesTokenCount") Integer candidatesTokenCount,
            @JsonProperty("totalTokenCount") Integer totalTokenCount,
            @JsonProperty("cachedContentTokenCount") Integer cachedContentTokenCount,
            @JsonProperty("thoughtsTokenCount") Integer thoughtsTokenCount) {

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private Integer promptTokenCount;
            private Integer candidatesTokenCount;
            private Integer totalTokenCount;
            private Integer cachedContentTokenCount;
            private Integer thoughtsTokenCount;

            private Builder() {}

            Builder promptTokenCount(Integer promptTokenCount) {
                this.promptTokenCount = promptTokenCount;
                return this;
            }

            Builder candidatesTokenCount(Integer candidatesTokenCount) {
                this.candidatesTokenCount = candidatesTokenCount;
                return this;
            }

            Builder totalTokenCount(Integer totalTokenCount) {
                this.totalTokenCount = totalTokenCount;
                return this;
            }

            Builder cachedContentTokenCount(Integer cachedContentTokenCount) {
                this.cachedContentTokenCount = cachedContentTokenCount;
                return this;
            }

            Builder thoughtsTokenCount(Integer thoughtsTokenCount) {
                this.thoughtsTokenCount = thoughtsTokenCount;
                return this;
            }

            GeminiUsageMetadata build() {
                return new GeminiUsageMetadata(
                        promptTokenCount,
                        candidatesTokenCount,
                        totalTokenCount,
                        cachedContentTokenCount,
                        thoughtsTokenCount);
            }
        }
    }
}
