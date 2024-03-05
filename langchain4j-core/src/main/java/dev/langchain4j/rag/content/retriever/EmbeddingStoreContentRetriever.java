package dev.langchain4j.rag.content.retriever;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.Builder;

import java.util.List;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.*;
import static java.util.stream.Collectors.toList;

/**
 * A {@link ContentRetriever} backed by an {@link EmbeddingStore}.
 * <br>
 * By default, this retriever fetches the 3 most similar {@link Content}s relevant to the provided {@link Query}.
 * <br>
 * <br>
 * Configurable parameters (optional):
 * <br>
 * - {@link #maxResults}: The maximum number of {@link Content}s to retrieve.
 * <br>
 * - {@link #minScore}: The minimum relevance score for the returned {@link Content}s.
 * {@link Content}s scoring below {@link #minScore} are excluded from the results.
 */
public class EmbeddingStoreContentRetriever implements ContentRetriever {

    public static final int DEFAULT_MAX_RESULTS = 3;
    public static final double DEFAULT_MIN_SCORE = 0;

    private final EmbeddingStore<TextSegment> embeddingStore;
    private final EmbeddingModel embeddingModel;
    private final int maxResults;
    private final double minScore;

    public EmbeddingStoreContentRetriever(EmbeddingStore<TextSegment> embeddingStore,
                                          EmbeddingModel embeddingModel) {
        this(embeddingStore, embeddingModel, DEFAULT_MAX_RESULTS, DEFAULT_MIN_SCORE);
    }

    public EmbeddingStoreContentRetriever(EmbeddingStore<TextSegment> embeddingStore,
                                          EmbeddingModel embeddingModel,
                                          int maxResults) {
        this(embeddingStore, embeddingModel, maxResults, DEFAULT_MIN_SCORE);
    }

    @Builder
    public EmbeddingStoreContentRetriever(EmbeddingStore<TextSegment> embeddingStore,
                                          EmbeddingModel embeddingModel,
                                          Integer maxResults,
                                          Double minScore) {
        this.embeddingStore = ensureNotNull(embeddingStore, "embeddingStore");
        this.embeddingModel = ensureNotNull(embeddingModel, "embeddingModel");
        this.maxResults = ensureGreaterThanZero(getOrDefault(maxResults, DEFAULT_MAX_RESULTS), "maxResults");
        this.minScore = ensureBetween(getOrDefault(minScore, DEFAULT_MIN_SCORE), 0, 1, "minScore");
    }

    @Override
    public List<Content> retrieve(Query query) {

        Embedding embeddedText = embeddingModel.embed(query.text()).content();

        List<EmbeddingMatch<TextSegment>> relevant = embeddingStore.findRelevant(embeddedText, maxResults, minScore);

        return relevant.stream()
                .map(EmbeddingMatch::embedded)
                .map(Content::from)
                .collect(toList());
    }
}
