package dev.langchain4j.store.embedding;

import dev.langchain4j.Experimental;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.filter.Filter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static dev.langchain4j.internal.Utils.randomUUID;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static java.util.Collections.singletonList;

/**
 * Represents a store for embeddings, also known as a vector database.
 *
 * @param <Embedded> The class of the object that has been embedded. Typically, this is {@link dev.langchain4j.data.segment.TextSegment}.
 */
public interface EmbeddingStore<Embedded> {

    /**
     * Adds a given embedding to the store.
     *
     * @param embedding The embedding to be added to the store.
     * @return The auto-generated ID associated with the added embedding.
     */
    String add(Embedding embedding);

    /**
     * Adds a given embedding to the store.
     *
     * @param id        The unique identifier for the embedding to be added.
     * @param embedding The embedding to be added to the store.
     */
    void add(String id, Embedding embedding);

    /**
     * Adds a given embedding and the corresponding content that has been embedded to the store.
     *
     * @param embedding The embedding to be added to the store.
     * @param embedded  Original content that was embedded.
     * @return The auto-generated ID associated with the added embedding.
     */
    String add(Embedding embedding, Embedded embedded);

    /**
     * Adds multiple embeddings to the store.
     *
     * @param embeddings A list of embeddings to be added to the store.
     * @return A list of auto-generated IDs associated with the added embeddings.
     */
    List<String> addAll(List<Embedding> embeddings);

    /**
     * Adds multiple embeddings and their corresponding contents that have been embedded to the store.
     *
     * @param embeddings A list of embeddings to be added to the store.
     * @param embedded   A list of original contents that were embedded.
     * @return A list of auto-generated IDs associated with the added embeddings.
     */
    default List<String> addAll(List<Embedding> embeddings, List<Embedded> embedded) {
        final List<String> ids = generateIds(embeddings.size());
        addAll(ids, embeddings, embedded);
        return ids;
    }

    /**
     * Adds multiple embeddings and their corresponding contents that have been embedded to the store.
     *
     * @param ids A list of IDs associated with the added embeddings.
     * @param embeddings A list of embeddings to be added to the store.
     * @param embedded   A list of original contents that were embedded.
     */
    default void addAll(List<String> ids, List<Embedding> embeddings, List<Embedded> embedded) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    /**
     * Removes a single embedding from the store by ID.
     *
     * @param id The unique ID of the embedding to be removed.
     */
    @Experimental
    default void remove(String id) {
        ensureNotBlank(id, "id");
        this.removeAll(singletonList(id));
    }

    /**
     * Removes all embeddings that match the specified IDs from the store.
     *
     * @param ids A collection of unique IDs of the embeddings to be removed.
     */
    @Experimental
    default void removeAll(Collection<String> ids) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    /**
     * Removes all embeddings that match the specified {@link Filter} from the store.
     *
     * @param filter The filter to be applied to the {@link Metadata} of the {@link TextSegment} during removal.
     *               Only embeddings whose {@code TextSegment}'s {@code Metadata}
     *               match the {@code Filter} will be removed.
     */
    @Experimental
    default void removeAll(Filter filter) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    /**
     * Removes all embeddings from the store.
     */
    @Experimental
    default void removeAll() {
        throw new UnsupportedOperationException("Not supported yet.");
    }


    /**
     * Generates list of UUID strings
     * @param n  - dimension of list
     */
    default List<String> generateIds(int n) {
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            ids.add(randomUUID());
        }
        return ids;
    }

    /**
     * Searches for the most similar (closest in the embedding space) {@link Embedding}s.
     * <br>
     * All search criteria are defined inside the {@link EmbeddingSearchRequest}.
     * <br>
     * {@link EmbeddingSearchRequest#filter()} can be used to filter by various metadata entries (e.g., user/memory ID).
     * Please note that not all {@link EmbeddingStore} implementations support {@link Filter}ing.
     *
     * @param request A request to search in an {@link EmbeddingStore}. Contains all search criteria.
     * @return An {@link EmbeddingSearchResult} containing all found {@link Embedding}s.
     */
    default EmbeddingSearchResult<Embedded> search(EmbeddingSearchRequest request) {
        if (request.filter() != null) {
            throw new UnsupportedOperationException("EmbeddingSearchRequest.Filter is not supported yet.");
        }

        List<EmbeddingMatch<Embedded>> matches =
                findRelevant(request.queryEmbedding(), request.maxResults(), request.minScore());
        return new EmbeddingSearchResult<>(matches);
    }

    /**
     * Finds the most relevant (closest in space) embeddings to the provided reference embedding.
     * By default, minScore is set to 0, which means that the results may include embeddings with low relevance.
     *
     * @param referenceEmbedding The embedding used as a reference. Returned embeddings should be relevant (closest) to this one.
     * @param maxResults         The maximum number of embeddings to be returned.
     * @return A list of embedding matches.
     * Each embedding match includes a relevance score (derivative of cosine distance),
     * ranging from 0 (not relevant) to 1 (highly relevant).
     * @deprecated as of 0.31.0, use {@link #search(EmbeddingSearchRequest)} instead.
     */
    @Deprecated(forRemoval = true)
    default List<EmbeddingMatch<Embedded>> findRelevant(Embedding referenceEmbedding, int maxResults) {
        return findRelevant(referenceEmbedding, maxResults, 0);
    }

    /**
     * Finds the most relevant (closest in space) embeddings to the provided reference embedding.
     *
     * @param referenceEmbedding The embedding used as a reference. Returned embeddings should be relevant (closest) to this one.
     * @param maxResults         The maximum number of embeddings to be returned.
     * @param minScore           The minimum relevance score, ranging from 0 to 1 (inclusive).
     *                           Only embeddings with a score of this value or higher will be returned.
     * @return A list of embedding matches.
     * Each embedding match includes a relevance score (derivative of cosine distance),
     * ranging from 0 (not relevant) to 1 (highly relevant).
     * @deprecated as of 0.31.0, use {@link #search(EmbeddingSearchRequest)} instead.
     */
    @Deprecated(forRemoval = true)
    default List<EmbeddingMatch<Embedded>> findRelevant(Embedding referenceEmbedding, int maxResults, double minScore) {
        EmbeddingSearchRequest embeddingSearchRequest = EmbeddingSearchRequest.builder()
                .queryEmbedding(referenceEmbedding)
                .maxResults(maxResults)
                .minScore(minScore)
                .build();
        EmbeddingSearchResult<Embedded> embeddingSearchResult = search(embeddingSearchRequest);
        return embeddingSearchResult.matches();
    }

    /**
     * Finds the most relevant (closest in space) embeddings to the provided reference embedding.
     * By default, minScore is set to 0, which means that the results may include embeddings with low relevance.
     *
     * @param memoryId           The memoryId used Distinguishing query requests from different users.
     * @param referenceEmbedding The embedding used as a reference. Returned embeddings should be relevant (closest) to this one.
     * @param maxResults         The maximum number of embeddings to be returned.
     * @return A list of embedding matches.
     * Each embedding match includes a relevance score (derivative of cosine distance),
     * ranging from 0 (not relevant) to 1 (highly relevant).
     * @deprecated as of 0.31.0, use {@link #search(EmbeddingSearchRequest)} instead.
     */
    @Deprecated(forRemoval = true)
    default List<EmbeddingMatch<Embedded>> findRelevant(
            Object memoryId, Embedding referenceEmbedding, int maxResults) {
        return findRelevant(memoryId, referenceEmbedding, maxResults, 0);
    }

    /**
     * Finds the most relevant (closest in space) embeddings to the provided reference embedding.
     *
     * @param memoryId           The memoryId used Distinguishing query requests from different users.
     * @param referenceEmbedding The embedding used as a reference. Returned embeddings should be relevant (closest) to this one.
     * @param maxResults         The maximum number of embeddings to be returned.
     * @param minScore           The minimum relevance score, ranging from 0 to 1 (inclusive).
     *                           Only embeddings with a score of this value or higher will be returned.
     * @return A list of embedding matches.
     * Each embedding match includes a relevance score (derivative of cosine distance),
     * ranging from 0 (not relevant) to 1 (highly relevant).
     * @deprecated as of 0.31.0, use {@link #search(EmbeddingSearchRequest)} instead.
     */
    @Deprecated(forRemoval = true)
    default List<EmbeddingMatch<Embedded>> findRelevant(
            Object memoryId, Embedding referenceEmbedding, int maxResults, double minScore) {
        throw new RuntimeException("Not implemented");
    }
}
