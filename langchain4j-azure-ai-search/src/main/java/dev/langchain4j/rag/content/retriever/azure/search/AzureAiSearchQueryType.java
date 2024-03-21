package dev.langchain4j.rag.content.retriever.azure.search;

import dev.langchain4j.data.segment.TextSegment;

public enum AzureAiSearchQueryType {

    /**
     * Uses the vector search algorithm to find the most similar {@link TextSegment}s.
     * More details can be found <a href="https://learn.microsoft.com/en-us/azure/search/vector-search-overview">here</a>.
     */
    VECTOR,

    /**
     * Uses the full text search to find the most similar {@code TextSegment}s.
     * More details can be found  <a href="https://learn.microsoft.com/en-us/azure/search/search-lucene-query-architecture">here</a>.
     */
    FULL_TEXT,

    /**
     * Uses the hybrid search (vector + full text) to find the most similar {@code TextSegment}s.
     * More details can be found  <a href="https://learn.microsoft.com/en-us/azure/search/hybrid-search-overview">here</a>.
     */
    HYBRID,

    /**
     * Uses the hybrid search (vector + full text) to find the most similar {@code TextSegment}s,
     * and uses the semantic re-ranker algorithm to rank the results.
     * More details can be found  <a href="https://learn.microsoft.com/en-us/azure/search/hybrid-search-ranking">here</a>.
     */
    HYBRID_WITH_RERANKING
}
