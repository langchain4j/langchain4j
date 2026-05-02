package dev.langchain4j.rag.content.retriever;

import static dev.langchain4j.data.segment.HypotheticalQuestionTextSegmentTransformer.ORIGINAL_TEXT_METADATA_KEY;
import static dev.langchain4j.internal.Exceptions.illegalArgument;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureBetween;
import static dev.langchain4j.internal.ValidationUtils.ensureGreaterThanZero;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static dev.langchain4j.spi.ServiceHelper.loadFactories;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.HypotheticalQuestionTextSegmentTransformer;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.ContentMetadata;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.spi.model.embedding.EmbeddingModelFactory;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * A {@link ContentRetriever} designed to work with
 * {@link HypotheticalQuestionTextSegmentTransformer}.
 * <br>
 * <br>
 * This retriever searches an {@link EmbeddingStore} where text segments have been ingested
 * using the <b>Hypothetical Question Embedding (HQE)</b> pattern: each stored embedding corresponds
 * to a hypothetical question, while the original text is stored in the segment's metadata
 * under the key {@value HypotheticalQuestionTextSegmentTransformer#ORIGINAL_TEXT_METADATA_KEY}.
 * This retriever expects the embedding store to return the original embedded {@link TextSegment}
 * along with that metadata for every match.
 * <br>
 * <br>
 * During retrieval, this component:
 * <ol>
 *   <li>Embeds the user query and searches the embedding store for matching hypothetical questions.</li>
 *   <li>Extracts the original text from each match's metadata.</li>
 *   <li>Deduplicates results by original segment, keeping the highest relevance score for each.</li>
 *   <li>Returns the deduplicated results as {@link Content} containing the original text.</li>
 * </ol>
 * <br>
 * Configurable parameters (optional):
 * <br>
 * - {@code maxResults}: The maximum number of {@link Content}s to return after deduplication.
 * Default: 3.
 * <br>
 * - {@code candidateMaxResults}: The maximum number of raw matches to retrieve before deduplication.
 * Default: {@code maxResults * 3}. Should be greater than or equal to {@code maxResults} to ensure enough
 * unique results after deduplication.
 * <br>
 * - {@code minScore}: The minimum relevance score. Default: 0.0.
 * <br>
 * - {@code filter}: A metadata {@link Filter}. Default: none.
 * <br>
 * - {@code displayName}: Display name for logging. Default: "Default".
 * <br>
 * <br>
 * Example usage:
 * <pre>{@code
 * ContentRetriever retriever = HypotheticalQuestionContentRetriever.builder()
 *         .embeddingStore(embeddingStore)
 *         .embeddingModel(embeddingModel)
 *         .maxResults(3)
 *         .build();
 *
 * RetrievalAugmentor augmentor = DefaultRetrievalAugmentor.builder()
 *         .contentRetriever(retriever)
 *         .build();
 * }</pre>
 *
 * @see HypotheticalQuestionTextSegmentTransformer
 * @see EmbeddingStoreContentRetriever
 */
public class HypotheticalQuestionContentRetriever implements ContentRetriever {

    public static final int DEFAULT_MAX_RESULTS = 3;
    public static final double DEFAULT_MIN_SCORE = 0.0;
    public static final String DEFAULT_DISPLAY_NAME = "Default";

    private static final Function<Query, Integer> DEFAULT_MAX_RESULTS_PROVIDER = query -> DEFAULT_MAX_RESULTS;
    private static final Function<Query, Double> DEFAULT_MIN_SCORE_PROVIDER = query -> DEFAULT_MIN_SCORE;
    private static final Function<Query, Filter> DEFAULT_FILTER_PROVIDER = query -> null;

    private final EmbeddingStore<TextSegment> embeddingStore;
    private final EmbeddingModel embeddingModel;
    private final Function<Query, Integer> maxResultsProvider;
    private final Function<Query, Integer> candidateMaxResultsProvider;
    private final Function<Query, Double> minScoreProvider;
    private final Function<Query, Filter> filterProvider;
    private final String displayName;

    private HypotheticalQuestionContentRetriever(
            EmbeddingStore<TextSegment> embeddingStore,
            EmbeddingModel embeddingModel,
            Function<Query, Integer> dynamicMaxResults,
            Function<Query, Integer> dynamicCandidateMaxResults,
            Function<Query, Double> dynamicMinScore,
            Function<Query, Filter> dynamicFilter,
            String displayName) {
        this.embeddingStore = ensureNotNull(embeddingStore, "embeddingStore");
        this.embeddingModel = ensureNotNull(
                getOrDefault(embeddingModel, HypotheticalQuestionContentRetriever::loadEmbeddingModel),
                "embeddingModel");
        this.maxResultsProvider = getOrDefault(dynamicMaxResults, DEFAULT_MAX_RESULTS_PROVIDER);
        this.candidateMaxResultsProvider =
                getOrDefault(dynamicCandidateMaxResults, query -> this.maxResultsProvider.apply(query) * 3);
        this.minScoreProvider = getOrDefault(dynamicMinScore, DEFAULT_MIN_SCORE_PROVIDER);
        this.filterProvider = getOrDefault(dynamicFilter, DEFAULT_FILTER_PROVIDER);
        this.displayName = getOrDefault(displayName, DEFAULT_DISPLAY_NAME);
    }

    @Override
    public List<Content> retrieve(Query query) {
        int maxResults = ensureGreaterThanZero(maxResultsProvider.apply(query), "maxResults");
        int candidateMaxResults =
                ensureGreaterThanZero(candidateMaxResultsProvider.apply(query), "candidateMaxResults");
        if (candidateMaxResults < maxResults) {
            throw illegalArgument(
                    "candidateMaxResults must be greater than or equal to maxResults, but is: %s < %s",
                    candidateMaxResults, maxResults);
        }
        double minScore = ensureBetween(minScoreProvider.apply(query), 0.0, 1.0, "minScore");
        Filter filter = filterProvider.apply(query);

        Embedding embeddedQuery = embeddingModel.embed(query.text()).content();
        EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                .query(query.text())
                .queryEmbedding(embeddedQuery)
                .maxResults(candidateMaxResults)
                .minScore(minScore)
                .filter(filter)
                .build();
        EmbeddingSearchResult<TextSegment> searchResult = embeddingStore.search(searchRequest);
        return deduplicate(searchResult.matches(), maxResults);
    }

    private List<Content> deduplicate(List<EmbeddingMatch<TextSegment>> matches, int maxResults) {
        LinkedHashMap<TextSegment, EmbeddingMatch<TextSegment>> bestMatchByOriginalSegment = new LinkedHashMap<>();

        for (EmbeddingMatch<TextSegment> match : matches) {
            TextSegment originalSegment = toOriginalSegment(match);
            EmbeddingMatch<TextSegment> existing = bestMatchByOriginalSegment.get(originalSegment);
            if (existing == null || match.score() > existing.score()) {
                bestMatchByOriginalSegment.put(originalSegment, match);
            }
        }

        return bestMatchByOriginalSegment.entrySet().stream()
                .sorted(Comparator.<Map.Entry<TextSegment, EmbeddingMatch<TextSegment>>>comparingDouble(
                                entry -> entry.getValue().score())
                        .reversed())
                .limit(maxResults)
                .map(entry -> {
                    EmbeddingMatch<TextSegment> match = entry.getValue();
                    return Content.from(
                            entry.getKey(),
                            Map.of(
                                    ContentMetadata.SCORE, match.score(),
                                    ContentMetadata.EMBEDDING_ID, match.embeddingId()));
                })
                .collect(Collectors.toList());
    }

    private TextSegment toOriginalSegment(EmbeddingMatch<TextSegment> match) {
        TextSegment embeddedSegment = ensureNotNull(
                match.embedded(),
                "HypotheticalQuestionContentRetriever requires EmbeddingMatch.embedded() to be present");
        Metadata metadata = embeddedSegment.metadata().copy();
        String originalText = ensureNotNull(
                metadata.getString(ORIGINAL_TEXT_METADATA_KEY),
                "HypotheticalQuestionContentRetriever requires metadata key '%s'",
                ORIGINAL_TEXT_METADATA_KEY);
        metadata.remove(ORIGINAL_TEXT_METADATA_KEY);
        return TextSegment.from(originalText, metadata);
    }

    @Override
    public String toString() {
        return "HypotheticalQuestionContentRetriever{" + "displayName='" + displayName + '\'' + '}';
    }

    private static EmbeddingModel loadEmbeddingModel() {
        Collection<EmbeddingModelFactory> factories = loadFactories(EmbeddingModelFactory.class);
        if (factories.size() > 1) {
            throw new RuntimeException("Conflict: multiple embedding models have been found in the classpath. "
                    + "Please explicitly specify the one you wish to use.");
        }

        for (EmbeddingModelFactory factory : factories) {
            return factory.create();
        }

        return null;
    }

    /**
     * Creates a new {@link HypotheticalQuestionContentRetrieverBuilder}.
     *
     * @return a new builder instance.
     */
    public static HypotheticalQuestionContentRetrieverBuilder builder() {
        return new HypotheticalQuestionContentRetrieverBuilder();
    }

    /**
     * Creates an instance of a {@code HypotheticalQuestionContentRetriever} from the specified {@link EmbeddingStore}
     * and {@link EmbeddingModel} found through SPI (see {@link EmbeddingModelFactory}).
     */
    public static HypotheticalQuestionContentRetriever from(EmbeddingStore<TextSegment> embeddingStore) {
        return builder().embeddingStore(embeddingStore).build();
    }

    public static class HypotheticalQuestionContentRetrieverBuilder {

        private EmbeddingStore<TextSegment> embeddingStore;
        private EmbeddingModel embeddingModel;
        private Integer maxResults;
        private Integer candidateMaxResults;
        private Double minScore;
        private Function<Query, Integer> dynamicMaxResults;
        private Function<Query, Integer> dynamicCandidateMaxResults;
        private Function<Query, Double> dynamicMinScore;
        private Function<Query, Filter> dynamicFilter;
        private String displayName;

        HypotheticalQuestionContentRetrieverBuilder() {}

        /**
         * Sets the {@link EmbeddingStore} to search. Mandatory.
         *
         * @param embeddingStore the embedding store.
         * @return this builder.
         */
        public HypotheticalQuestionContentRetrieverBuilder embeddingStore(EmbeddingStore<TextSegment> embeddingStore) {
            this.embeddingStore = embeddingStore;
            return this;
        }

        /**
         * Sets the {@link EmbeddingModel} to use for embedding queries. Mandatory.
         *
         * @param embeddingModel the embedding model.
         * @return this builder.
         */
        public HypotheticalQuestionContentRetrieverBuilder embeddingModel(EmbeddingModel embeddingModel) {
            this.embeddingModel = embeddingModel;
            return this;
        }

        /**
         * Sets the maximum number of {@link Content}s to return after deduplication.
         * Default: {@value #DEFAULT_MAX_RESULTS}.
         *
         * @param maxResults the maximum number of results.
         * @return this builder.
         */
        public HypotheticalQuestionContentRetrieverBuilder maxResults(Integer maxResults) {
            if (maxResults != null) {
                this.maxResults = ensureGreaterThanZero(maxResults, "maxResults");
                this.dynamicMaxResults = query -> this.maxResults;
            }
            return this;
        }

        /**
         * Sets the maximum number of raw matches to retrieve before deduplication.
         * Should be greater than or equal to {@code maxResults} to ensure enough unique results after deduplication.
         * Default: {@code maxResults * 3}.
         *
         * @param candidateMaxResults the maximum number of raw candidates.
         * @return this builder.
         */
        public HypotheticalQuestionContentRetrieverBuilder candidateMaxResults(Integer candidateMaxResults) {
            if (candidateMaxResults != null) {
                this.candidateMaxResults = ensureGreaterThanZero(candidateMaxResults, "candidateMaxResults");
                this.dynamicCandidateMaxResults = query -> this.candidateMaxResults;
            }
            return this;
        }

        /**
         * Sets the minimum relevance score for returned results.
         * Default: {@value #DEFAULT_MIN_SCORE}.
         *
         * @param minScore the minimum score, between 0 and 1.
         * @return this builder.
         */
        public HypotheticalQuestionContentRetrieverBuilder minScore(Double minScore) {
            if (minScore != null) {
                this.minScore = ensureBetween(minScore, 0.0, 1.0, "minScore");
                this.dynamicMinScore = query -> this.minScore;
            }
            return this;
        }

        /**
         * Sets a metadata {@link Filter} applied during the embedding store search.
         *
         * @param filter the filter.
         * @return this builder.
         */
        public HypotheticalQuestionContentRetrieverBuilder filter(Filter filter) {
            if (filter != null) {
                this.dynamicFilter = query -> filter;
            }
            return this;
        }

        /**
         * Sets a function that dynamically defines {@code maxResults} based on the {@link Query}.
         *
         * @param dynamicMaxResults function that returns max results; may be {@code null}.
         * @return this builder.
         */
        public HypotheticalQuestionContentRetrieverBuilder dynamicMaxResults(
                Function<Query, Integer> dynamicMaxResults) {
            if (dynamicMaxResults != null) {
                this.maxResults = null;
                this.dynamicMaxResults = query -> ensureGreaterThanZero(dynamicMaxResults.apply(query), "maxResults");
            }
            return this;
        }

        /**
         * Sets a function that dynamically defines raw candidate matches based on the {@link Query}.
         *
         * @param dynamicCandidateMaxResults function that returns candidate max results; may be {@code null}.
         * @return this builder.
         */
        public HypotheticalQuestionContentRetrieverBuilder dynamicCandidateMaxResults(
                Function<Query, Integer> dynamicCandidateMaxResults) {
            if (dynamicCandidateMaxResults != null) {
                this.candidateMaxResults = null;
                this.dynamicCandidateMaxResults =
                        query -> ensureGreaterThanZero(dynamicCandidateMaxResults.apply(query), "candidateMaxResults");
            }
            return this;
        }

        /**
         * Sets a function that dynamically defines {@code minScore} based on the {@link Query}.
         *
         * @param dynamicMinScore function that returns min score; may be {@code null}.
         * @return this builder.
         */
        public HypotheticalQuestionContentRetrieverBuilder dynamicMinScore(Function<Query, Double> dynamicMinScore) {
            if (dynamicMinScore != null) {
                this.minScore = null;
                this.dynamicMinScore = query -> ensureBetween(dynamicMinScore.apply(query), 0.0, 1.0, "minScore");
            }
            return this;
        }

        /**
         * Sets a function that dynamically defines metadata filtering based on the {@link Query}.
         *
         * @param dynamicFilter function that returns a filter; may be {@code null}.
         * @return this builder.
         */
        public HypotheticalQuestionContentRetrieverBuilder dynamicFilter(Function<Query, Filter> dynamicFilter) {
            if (dynamicFilter != null) {
                this.dynamicFilter = dynamicFilter;
            }
            return this;
        }

        /**
         * Sets the display name for logging purposes.
         * Default: {@value #DEFAULT_DISPLAY_NAME}.
         *
         * @param displayName the display name.
         * @return this builder.
         */
        public HypotheticalQuestionContentRetrieverBuilder displayName(String displayName) {
            this.displayName = displayName;
            return this;
        }

        /**
         * Builds a new {@link HypotheticalQuestionContentRetriever}.
         *
         * @return a new retriever instance.
         */
        public HypotheticalQuestionContentRetriever build() {
            if (maxResults != null && candidateMaxResults != null && candidateMaxResults < maxResults) {
                throw illegalArgument(
                        "candidateMaxResults must be greater than or equal to maxResults, but is: %s < %s",
                        candidateMaxResults, maxResults);
            }
            return new HypotheticalQuestionContentRetriever(
                    embeddingStore,
                    embeddingModel,
                    dynamicMaxResults,
                    dynamicCandidateMaxResults,
                    dynamicMinScore,
                    dynamicFilter,
                    displayName);
        }
    }
}
