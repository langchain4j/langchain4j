package dev.langchain4j.store.embedding.qdrant;

/**
 * Search modes for the Qdrant embedding store.
 */
public enum SearchMode {
    /** Standard dense vector search. */
    VECTOR,
    /** Hybrid search: sparse + dense prefetch fused with RRF. */
    HYBRID
}
