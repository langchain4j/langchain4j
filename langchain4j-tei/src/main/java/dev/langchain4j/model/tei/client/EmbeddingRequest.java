package dev.langchain4j.model.tei.client;

import lombok.Builder;

import java.util.List;

@Builder
public class EmbeddingRequest {

    private final List<String> input;

}
