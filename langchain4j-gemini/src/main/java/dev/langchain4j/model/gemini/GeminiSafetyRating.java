package dev.langchain4j.model.gemini;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class GeminiSafetyRating {
    private GeminiHarmCategory category;
    private GeminiHarmBlockThreshold threshold;
    private Boolean blocked;
}
