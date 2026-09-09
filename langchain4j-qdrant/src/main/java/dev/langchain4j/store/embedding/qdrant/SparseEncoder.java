package dev.langchain4j.store.embedding.qdrant;

/**
 * Encodes text into a {@link SparseVector} for hybrid search.
 */
public interface SparseEncoder {
    SparseVector encode(String text);
}
