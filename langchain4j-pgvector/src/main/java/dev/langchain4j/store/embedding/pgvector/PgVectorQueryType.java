package dev.langchain4j.store.embedding.pgvector;

import dev.langchain4j.data.segment.TextSegment;

/**
 * Query type to be used when searching for similar {@link TextSegment}s in PGVector.
 */
public enum PgVectorQueryType {

    /**
     * Uses vector similarity search to find the most similar (by cosine distance) {@code TextSegment}s.
     * Default behavior.
     */
    VECTOR,

    /**
     * Uses PostgreSQL full-text search (FTS) with ts_vector and ts_query to find matching {@code TextSegment}s.
     * Requires the text column to be indexed with a GIN index on the tsvector.
     */
    FULL_TEXT,

    /**
     * Uses hybrid search combining vector similarity and full-text search.
     * The final score is calculated as: (vectorWeight * vectorScore) + (textWeight * textScore)
     * Default weights are 0.6 for vector and 0.4 for full-text search.
     * <p>
     * This approach combines the semantic understanding of vector search with the keyword matching
     * capabilities of full-text search, often providing better results for RAG applications.
     */
    HYBRID
}
