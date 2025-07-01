package dev.langchain4j.model.vertexai.gemini;

import com.google.cloud.vertexai.api.Candidate;
import dev.langchain4j.model.output.FinishReason;

class FinishReasonMapper {

    static FinishReason map(Candidate.FinishReason finishReason) {
        switch (finishReason) {
            case STOP:
                return FinishReason.STOP;
            case MAX_TOKENS:
                return FinishReason.LENGTH;
            case SAFETY:
                return FinishReason.CONTENT_FILTER;
        }
        return FinishReason.OTHER;
    }
}
