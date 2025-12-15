package dev.langchain4j.rag.content.retriever;

import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.query.Query;

import java.util.List;

/**
 * Retrieves {@link Content}s from an underlying data source using a given {@link Query}.
 * <br>
 * The goal is to retrieve only relevant {@link Content}s in relation to a given {@link Query}.
 * <br>
 * The underlying data source can be virtually anything:
 * <pre>
 * - Embedding (vector) store (see {@link EmbeddingStoreContentRetriever})
 * - Full-text search engine (see {@code AzureAiSearchContentRetriever} in the {@code langchain4j-azure-ai-search} module)
 * - Hybrid of vector and full-text search (see {@code AzureAiSearchContentRetriever} in the {@code langchain4j-azure-ai-search} module)
 * - Web Search Engine (see {@link WebSearchContentRetriever})
 * - Knowledge graph (see {@code Neo4jContentRetriever} in the {@code langchain4j-community-neo4j-retriever} module)
 * - SQL database (see {@code SqlDatabaseContentRetriever} in the {@code langchain4j-experimental-sql} module)
 * - etc.
 * </pre>
 *
 * @see EmbeddingStoreContentRetriever
 * @see WebSearchContentRetriever
 */
public interface ContentRetriever {

    /**
     * Retrieves relevant {@link Content}s using a given {@link Query}.
     * The {@link Content}s are sorted by relevance, with the most relevant {@link Content}s appearing
     * at the beginning of the returned {@code List<Content>}.
     *
     * @param query The {@link Query} to use for retrieval.
     * @return A list of retrieved {@link Content}s.
     */
    List<Content> retrieve(Query query);
}
