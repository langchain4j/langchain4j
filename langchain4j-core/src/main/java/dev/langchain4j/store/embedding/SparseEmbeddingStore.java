package dev.langchain4j.store.embedding;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.embedding.SparseEmbedding;
import dev.langchain4j.data.segment.TextSegment;
import java.util.List;

public interface SparseEmbeddingStore<T> extends EmbeddingStore<T> {
    /**
     * Adds multiple sparse embeddings to the store.
     *
     * @param embeddings A list of sparse embeddings to be added to the store.
     */
    default void addAllSparse(List<String> ids, List<SparseEmbedding> embeddings, List<TextSegment> textSegments) {
        throw new UnsupportedOperationException("Sparse not supported");
    }

    default void addAllHybrid(
            List<String> ids,
            List<Embedding> denseEmbeddings,
            List<SparseEmbedding> sparseEmbeddings,
            List<TextSegment> textSegments) {
        throw new UnsupportedOperationException("Hybrid not supported");
    }
}
