package dev.langchain4j.model.googleai;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
class GeminiFunctionDeclaration {
    private String name;
    private String description;
    private GeminiSchema parameters;
}
