package dev.langchain4j.model.googleai;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
class GeminiGenerationConfig {
    private List<String> stopSequences;
    private String responseMimeType;
    private GeminiSchema responseSchema;
    private Integer candidateCount = 1;
    private Integer maxOutputTokens = 8192;
    private Double temperature = 1.0;
    private Integer topK = 64;
    private Double topP = 0.95;
}
