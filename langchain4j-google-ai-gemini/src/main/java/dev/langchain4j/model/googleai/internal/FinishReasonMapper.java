package dev.langchain4j.model.googleai.internal;

import dev.langchain4j.Internal;
import dev.langchain4j.model.output.FinishReason;

@Internal
public class FinishReasonMapper {
    private FinishReasonMapper() {}

    public static FinishReason fromGFinishReasonToFinishReason(GeminiFinishReason geminiFinishReason) {
        switch (geminiFinishReason) {
            case STOP:
                return FinishReason.STOP;
            case BLOCKLIST:
            case PROHIBITED_CONTENT:
            case RECITATION:
            case SPII:
            case SAFETY:
            case LANGUAGE:
                return FinishReason.CONTENT_FILTER;
            case MAX_TOKENS:
                return FinishReason.LENGTH;
            case MALFORMED_FUNCTION_CALL:
            case FINISH_REASON_UNSPECIFIED:
            case OTHER:
                return FinishReason.OTHER;
            default:
                return FinishReason.OTHER;
        }
    }
}
