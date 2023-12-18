package dev.langchain4j.model.ollama;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
class CompletionResponse {

    private String model;
    private String createdAt;
    private String response;
    private Boolean done;
    private Integer promptEvalCount;
    private Integer evalCount;
}
