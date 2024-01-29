package dev.langchain4j.model.ollama;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OllamaModelCard {

    private String modelfile;
    private String parameters;
    private String template;
    private OllamaModelDetails details;
}
