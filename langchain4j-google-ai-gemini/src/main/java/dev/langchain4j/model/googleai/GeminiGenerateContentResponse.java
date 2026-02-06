package dev.langchain4j.model.googleai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
record GeminiGenerateContentResponse(
        String responseId,
        String modelVersion,
        List<GeminiCandidate> candidates,
        GeminiUsageMetadata usageMetadata,
        GroundingMetadata groundingMetadata) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    record GeminiCandidate(
            GeminiContent content,
            GeminiFinishReason finishReason,
            GeminiUrlContextMetadata urlContextMetadata,
            GroundingMetadata groundingMetadata) {
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
    record GeminiUrlContextMetadata(List<GeminiUrlMetadata> urlMetadata) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record GeminiUrlMetadata(String retrievedUrl, GeminiUrlRetrievalStatus urlRetrievalStatus) {}

    enum GeminiUrlRetrievalStatus {
        URL_RETRIEVAL_STATUS_UNSPECIFIED,
        URL_RETRIEVAL_STATUS_SUCCESS,
        URL_RETRIEVAL_STATUS_ERROR,
        URL_RETRIEVAL_STATUS_PAYWALL,
        URL_RETRIEVAL_STATUS_UNSAFE
    }
}
