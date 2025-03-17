package dev.langchain4j.model.googleai;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
class GeminiPromptFeedback {
    private GeminiBlockReason blockReason;
    private List<GeminiSafetyRating> safetyRatings;
}
