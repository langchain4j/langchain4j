package dev.langchain4j.model.gemini;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class GeminiSafetySetting {
    private GeminiHarmCategory category;
    private GeminiHarmBlockThreshold threshold;
}
