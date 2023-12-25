package dev.langchain4j.model.ollama;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
class ChatResponse {

    private String model;
    private String createdAt;
    private Message message;
    private Boolean done;
    private Integer promptEvalCount;
    private Integer evalCount;
}
