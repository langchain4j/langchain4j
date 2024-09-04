package dev.langchain4j.model.googleai;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class GeminiToolConfig {
    private GeminiFunctionCallingConfig functionCallingConfig;
}
