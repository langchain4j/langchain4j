package dev.langchain4j.store.embedding.listener;

import dev.langchain4j.Experimental;

/**
 * Operation executed against an embedding store.
 *
 * @since 1.11.0
 */
@Experimental
public enum EmbeddingStoreOperation {
    ADD,
    ADD_ALL,
    SEARCH,
    REMOVE,
    REMOVE_ALL_IDS,
    REMOVE_ALL_FILTER,
    REMOVE_ALL
}
