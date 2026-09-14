package dev.langchain4j.store.embedding.qdrant;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class QdrantEmbeddingStoreHybridBuilderTest {

    @Test
    void should_reject_hybrid_without_sparse_encoder_via_host_constructor() {
        assertThatThrownBy(() -> new QdrantEmbeddingStore(
                        "c", "localhost", 6334, false, "text", null, SearchMode.HYBRID, null, "dense", "sparse"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SparseEncoder");
    }

    @Test
    void should_reject_hybrid_without_sparse_encoder_via_client_constructor() {
        assertThatThrownBy(
                        () -> new QdrantEmbeddingStore(null, "c", "text", SearchMode.HYBRID, null, "dense", "sparse"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SparseEncoder");
    }

    @Test
    void should_reject_hybrid_without_sparse_encoder_via_builder() {
        assertThatThrownBy(() -> QdrantEmbeddingStore.builder()
                        .collectionName("c")
                        .searchMode(SearchMode.HYBRID)
                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SparseEncoder");
    }
}
