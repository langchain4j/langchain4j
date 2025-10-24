package dev.langchain4j.model.googleai;

import dev.langchain4j.model.googleai.GeminiGenerateContentResponse.GeminiCandidate.GeminiFinishReason;
import dev.langchain4j.model.output.FinishReason;

class FinishReasonMapper {
    static FinishReason fromGFinishReasonToFinishReason(GeminiFinishReason geminiFinishReason) {
        return switch (geminiFinishReason) {
            case STOP -> FinishReason.STOP;
            case BLOCKLIST, PROHIBITED_CONTENT, RECITATION, SPII, SAFETY, LANGUAGE -> FinishReason.CONTENT_FILTER;
            case MAX_TOKENS -> FinishReason.LENGTH;
            case MALFORMED_FUNCTION_CALL, FINISH_REASON_UNSPECIFIED, OTHER -> FinishReason.OTHER;
        };
    }
}
