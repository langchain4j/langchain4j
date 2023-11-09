package dev.langchain4j.model.ollama;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CompletionRequest {

    /**
     * model name
     */
    private String model;
    /**
     * the prompt to generate a response for
     */
    private String prompt;
    private Options options;
    private Boolean stream;
}
