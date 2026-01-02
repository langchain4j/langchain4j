package dev.langchain4j.store.embedding.listener;

/**
 * Operation executed against an embedding store.
 */
public enum EmbeddingStoreOperation {
    ADD,
    ADD_ALL,
    SEARCH,
    REMOVE,
    REMOVE_ALL_IDS,
    REMOVE_ALL_FILTER,
    REMOVE_ALL
}
