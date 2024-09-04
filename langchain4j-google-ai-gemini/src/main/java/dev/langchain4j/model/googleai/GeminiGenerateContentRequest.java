package dev.langchain4j.model.googleai;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
class GeminiGenerateContentRequest {
    private List<GeminiContent> contents;
    private GeminiTool tools;
    private GeminiToolConfig toolConfig;
    private List<GeminiSafetySetting> safetySettings;
    private GeminiContent systemInstruction;
    private GeminiGenerationConfig generationConfig;
    private String cachedContent;
}
