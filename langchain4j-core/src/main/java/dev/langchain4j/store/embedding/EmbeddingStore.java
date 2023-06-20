package dev.langchain4j.store.embedding;

import dev.langchain4j.data.embedding.Embedding;

import java.util.List;

public interface EmbeddingStore<Embedded> {

    String add(Embedding embedding);

    void add(String id, Embedding embedding);

    String add(Embedding embedding, Embedded embedded);

    List<String> addAll(List<Embedding> embeddings);

    List<String> addAll(List<Embedding> embeddings, List<Embedded> embedded);

    List<EmbeddingMatch<Embedded>> findRelevant(Embedding referenceEmbedding, int maxResults);
}
