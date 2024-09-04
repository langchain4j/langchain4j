package dev.langchain4j.model.gemini;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@AllArgsConstructor
public class GeminiToolConfig {
    private GeminiFunctionCallingConfig functionCallingConfig;
}
