package dev.langchain4j.model.jinaAi;

import dev.langchain4j.data.embedding.Embedding;

import java.util.List;

public class EmbeddingResponse {
    Usage usage;
    List<JinaAiEmbedding> embeddingList;
}
