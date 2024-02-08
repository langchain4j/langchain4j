package dev.langchain4j.model.nomic;

import lombok.Builder;

import java.util.List;

@Builder
class EmbeddingRequest {

    private String model;
    private List<String> texts;
    private String taskType;
}
