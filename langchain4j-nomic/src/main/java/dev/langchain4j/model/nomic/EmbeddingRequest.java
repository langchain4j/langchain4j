package dev.langchain4j.model.nomic;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Builder
@Getter
class EmbeddingRequest {

    private String model;
    private List<String> texts;
    private String taskType;
}
