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
        @JsonProperty("groundingMetadata") GroundingMetadata groundingMetadata) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    record GeminiCandidate(
            @JsonProperty("content") GeminiContent content,
            @JsonProperty("finishReason") GeminiFinishReason finishReason,
            @JsonProperty("urlContextMetadata") GeminiUrlContextMetadata urlContextMetadata,
            @JsonProperty("groundingMetadata") GroundingMetadata groundingMetadata) {
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
            MALFORMED_FUNCTION_CALL
        }
    }

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
