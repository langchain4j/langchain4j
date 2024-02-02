package dev.langchain4j.retriever;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingStore;

import java.util.List;
import java.util.Objects;

import static java.util.stream.Collectors.toList;

/**
 * A Retriever which wraps an {@link EmbeddingStore} to find relevant items.
 */
@SuppressWarnings("deprecation")
public class EmbeddingStoreRetriever implements Retriever<TextSegment> {

    private final EmbeddingStore<TextSegment> embeddingStore;
    private final EmbeddingModel embeddingModel;
    private final int maxResults;
    private final double minScore;

    /**
     * Creates a new instance of {@link EmbeddingStoreRetriever}.
     *
     * @param embeddingStore The store to be used for retrieving relevant items.
     * @param embeddingModel The model to be used for embedding the text.
     * @param maxResults The maximum number of results to be returned.
     * @param minScore The minimum score for the results to be returned, or null to default
     *         to 0.
     */
    public EmbeddingStoreRetriever(EmbeddingStore<TextSegment> embeddingStore,
                                   EmbeddingModel embeddingModel,
                                   int maxResults,
                                   Double minScore) {
        this.embeddingStore = Objects.requireNonNull(embeddingStore, "embeddingStore");
        this.embeddingModel = Objects.requireNonNull(embeddingModel, "embeddingModel");
        this.maxResults = maxResults;
        this.minScore = minScore == null ? 0 : minScore;
    }

    @Override
    public List<TextSegment> findRelevant(String text) {
        Embedding embeddedText = embeddingModel.embed(text).content();

        List<EmbeddingMatch<TextSegment>> relevant;
        relevant = embeddingStore.findRelevant(embeddedText, maxResults, minScore);

        return relevant.stream()
                .map(EmbeddingMatch::embedded)
                .collect(toList());
    }

    /**
     * Creates a new instance of {@link EmbeddingStoreRetriever}.
     *
     * <p>{@code maxResults} defaults to 2; {@code minScore} to 0.</p>
     *
     * @param embeddingStore The store to be used for retrieving relevant items.
     * @param embeddingModel The model to be used for embedding the text.
     * @return A new instance of {@link EmbeddingStoreRetriever}.
     */
    public static EmbeddingStoreRetriever from(
            EmbeddingStore<TextSegment> embeddingStore,
            EmbeddingModel embeddingModel) {
        return new EmbeddingStoreRetriever(embeddingStore, embeddingModel, 2, null);
    }

    /**
     * Creates a new instance of {@link EmbeddingStoreRetriever}.
     *
     * <p>{@code minScore} defaults 0.</p>
     *
     * @param embeddingStore The store to be used for retrieving relevant items.
     * @param embeddingModel The model to be used for embedding the text.
     * @param maxResults The maximum number of results to be returned.
     * @return A new instance of {@link EmbeddingStoreRetriever}.
     */
    public static EmbeddingStoreRetriever from(
            EmbeddingStore<TextSegment> embeddingStore,
            EmbeddingModel embeddingModel,
            int maxResults) {
        return new EmbeddingStoreRetriever(embeddingStore, embeddingModel, maxResults, null);
    }

    /**
     * Creates a new instance of {@link EmbeddingStoreRetriever}.
     *
     * @param embeddingStore The store to be used for retrieving relevant items.
     * @param embeddingModel The model to be used for embedding the text.
     * @param maxResults The maximum number of results to be returned.
     * @param minScore The minimum score for the results to be returned.
     * @return A new instance of {@link EmbeddingStoreRetriever}.
     */
    public static EmbeddingStoreRetriever from(
            EmbeddingStore<TextSegment> embeddingStore,
            EmbeddingModel embeddingModel,
            int maxResults,
            Double minScore) {
        return new EmbeddingStoreRetriever(embeddingStore, embeddingModel, maxResults, minScore);
    }

    public EmbeddingStore<TextSegment> getEmbeddingStore() {
        return this.embeddingStore;
    }

    public EmbeddingModel getEmbeddingModel() {
        return this.embeddingModel;
    }

    public int getMaxResults() {
        return this.maxResults;
    }

    public double getMinScore() {
        return this.minScore;
    }

    @Override
    public boolean equals(final Object o) {
        if (o == this) return true;
        if (!(o instanceof EmbeddingStoreRetriever)) return false;
        final EmbeddingStoreRetriever other = (EmbeddingStoreRetriever) o;
        return Objects.equals(embeddingStore, other.embeddingStore)
                && Objects.equals(embeddingModel, other.embeddingModel)
               && (maxResults == other.maxResults)
               && (Double.compare(minScore, other.minScore) == 0);
    }

    @Override
    public int hashCode() {
        return Objects.hash(embeddingStore, embeddingModel, minScore, maxResults);
    }
}
