package dev.langchain4j.model.ollama;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
class CompletionRequest {

    private String model;
    private String system;
    private String prompt;
    private Options options;
    private String format;
    private Boolean stream;
}
