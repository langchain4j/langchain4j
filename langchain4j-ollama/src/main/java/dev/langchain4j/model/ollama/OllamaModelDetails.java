package dev.langchain4j.model.ollama;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
class OllamaModelDetails {
    private String format;
    private String family;
    private List<String> families;
    private String parameterSize;
    private String quantizationLevel;
}
