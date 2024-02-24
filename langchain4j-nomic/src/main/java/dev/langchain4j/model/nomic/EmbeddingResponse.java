package dev.langchain4j.model.nomic;

import lombok.Getter;

import java.util.List;

@Getter
class EmbeddingResponse {

    private List<float[]> embeddings;
    private Usage usage;
}
