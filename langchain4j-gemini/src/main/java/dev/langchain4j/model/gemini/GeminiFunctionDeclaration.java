package dev.langchain4j.model.gemini;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class GeminiFunctionDeclaration {
    private String name;
    private String description;
    private GeminiSchema parameters;
}
