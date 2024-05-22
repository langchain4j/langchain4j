package dev.langchain4j.model.jina.internal.api;

import lombok.Builder;

import java.util.List;

@Builder
public class EmbeddingRequest {

    public String model;
    public List<String> input;
}
