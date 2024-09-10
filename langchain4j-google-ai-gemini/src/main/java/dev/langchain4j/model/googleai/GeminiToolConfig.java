package dev.langchain4j.model.googleai;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
class GeminiToolConfig {
    private GeminiFunctionCallingConfig functionCallingConfig;
}
