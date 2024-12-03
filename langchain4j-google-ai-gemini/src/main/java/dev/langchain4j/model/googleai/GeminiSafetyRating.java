package dev.langchain4j.model.googleai;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
class GeminiSafetyRating {
    private GeminiHarmCategory category;
    private GeminiHarmBlockThreshold threshold;
    private Boolean blocked;
}
