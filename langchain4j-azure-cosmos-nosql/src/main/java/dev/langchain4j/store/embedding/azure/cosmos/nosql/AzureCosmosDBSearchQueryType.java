package dev.langchain4j.store.embedding.azure.cosmos.nosql;

import dev.langchain4j.data.segment.TextSegment;

public enum AzureCosmosDBSearchQueryType {

    /**
     * Uses the vector search algorithm to find the most similar {@link TextSegment}s.
     * More details can be found <a href="https://learn.microsoft.com/en-us/azure/cosmos-db/nosql/vector-search">here</a>.
     */
    VECTOR,

    /**
     * Uses the full text search to find the most similar {@code TextSegment}s.
     * More details can be found  <a href="https://learn.microsoft.com/en-us/azure/cosmos-db/gen-ai/full-text-search">here</a>.
     */
    FULL_TEXT_SEARCH,

    /**
     * Uses the full text search to find the most similar {@code TextSegment}s.
     * More details can be found  <a href="https://learn.microsoft.com/en-us/azure/cosmos-db/gen-ai/full-text-search">here</a>.
     */
    FULL_TEXT_RANKING,

    /**
     * Uses the hybrid search (vector + full text) to find the most similar {@code TextSegment}s.
     * More details can be found  <a href="https://learn.microsoft.com/en-us/azure/cosmos-db/gen-ai/hybrid-search">here</a>.
     */
    HYBRID
}
