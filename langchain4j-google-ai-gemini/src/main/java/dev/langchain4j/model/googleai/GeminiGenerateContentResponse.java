package dev.langchain4j.model.googleai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
record GeminiGenerateContentResponse(
        String responseId, String modelVersion, List<GeminiCandidate> candidates, GeminiUsageMetadata usageMetadata) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    record GeminiCandidate(GeminiContent content, GeminiFinishReason finishReason) {
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
    record GeminiUsageMetadata(Integer promptTokenCount, Integer candidatesTokenCount, Integer totalTokenCount) {

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private Integer promptTokenCount;
            private Integer candidatesTokenCount;
            private Integer totalTokenCount;

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

            GeminiUsageMetadata build() {
                return new GeminiUsageMetadata(promptTokenCount, candidatesTokenCount, totalTokenCount);
            }
        }
    }
}
