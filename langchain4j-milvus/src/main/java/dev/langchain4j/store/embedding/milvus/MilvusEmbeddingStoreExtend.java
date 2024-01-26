package dev.langchain4j.store.embedding.milvus;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingStore;

import java.util.List;

/**
 * Milvus Embedding Store extend
 */
public interface MilvusEmbeddingStoreExtend<Embedded> extends EmbeddingStore<Embedded> {

    /**
     * Finds the most relevant (closest in space) embeddings to the provided reference embedding by specified partitions.
     * Currently only milvus supports it.
     *
     * <p>Default implementation throws an exception.
     *
     * @param referenceEmbedding The embedding used as a reference. Returned embeddings should be relevant (closest) to this one.
     * @param maxResults         The maximum number of embeddings to be returned.
     * @param partitionNames     The range of partitions used for querying, Can be empty.
     * @return A list of embedding matches.
     * Each embedding match includes a relevance score (derivative of cosine distance),
     * ranging from 0 (not relevant) to 1 (highly relevant).
     */
    default List<EmbeddingMatch<Embedded>> findRelevant(Embedding referenceEmbedding, int maxResults, List<String> partitionNames) {
        return findRelevant(referenceEmbedding, maxResults, 0, partitionNames);
    }

    /**
     * Finds the most relevant (closest in space) embeddings to the provided reference embedding by specified partitions.
     * Currently only milvus supports it.
     *
     * @param referenceEmbedding The embedding used as a reference. Returned embeddings should be relevant (closest) to this one.
     * @param maxResults         The maximum number of embeddings to be returned.
     * @param minScore           The minimum relevance score, ranging from 0 to 1 (inclusive).
     *                           Only embeddings with a score of this value or higher will be returned.
     * @param partitionNames     The range of partitions used for querying, Can be empty.
     * @return A list of embedding matches.
     * Each embedding match includes a relevance score (derivative of cosine distance),
     * ranging from 0 (not relevant) to 1 (highly relevant).
     */
    List<EmbeddingMatch<Embedded>> findRelevant(Embedding referenceEmbedding, int maxResults, double minScore, List<String> partitionNames);

}
