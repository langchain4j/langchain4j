package dev.langchain4j.model.googleai;

import lombok.Data;

import java.util.List;

@Data
class GoogleAiBatchEmbeddingResponse {
    List<GoogleAiEmbeddingResponseValues> embeddings;
}
