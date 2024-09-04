package dev.langchain4j.model.googleai;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class GeminiGenerationConfig {
    private List<String> stopSequences;
    private String responseMimeType;
    private GeminiSchema responseSchema;
    private int candidateCount = 1;
    private int maxOutputTokens = 8192;
    private double temperature = 1.0;
    private int topK = 64;
    private double topP = 0.95;
}
