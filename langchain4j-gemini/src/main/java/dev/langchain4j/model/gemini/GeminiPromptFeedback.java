package dev.langchain4j.model.gemini;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class GeminiPromptFeedback {
    private GeminiBlockReason blockReason;
    private List<GeminiSafetyRating> safetyRatings;
}
