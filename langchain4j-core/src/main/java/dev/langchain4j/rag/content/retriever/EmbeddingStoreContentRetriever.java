package dev.langchain4j.rag.content.retriever;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureBetween;
import static dev.langchain4j.internal.ValidationUtils.ensureGreaterThanZero;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static dev.langchain4j.spi.ServiceHelper.loadFactories;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.ContentMetadata;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.spi.model.embedding.EmbeddingModelFactory;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * A {@link ContentRetriever} that retrieves from an {@link EmbeddingStore}.
 * <br>
 * By default, it retrieves the 3 most similar {@link Content}s to the provided {@link Query},
 * without any {@link Filter}ing.
 * <br>
 * <br>
 * Configurable parameters (optional):
 * <br>
 * - {@code displayName}: Display name for logging purposes, e.g. when multiple instances are used.
 * <br>
 * - {@code maxResults}: The maximum number of {@link Content}s to retrieve.
 * <br>
 * - {@code dynamicMaxResults}: It is a {@link Function} that accepts a {@link Query} and returns a {@code maxResults} value.
 * It can be used to dynamically define {@code maxResults} value, depending on factors such as the query,
 * the user (using Metadata#chatMemoryId()} from {@link Query#metadata()}), etc.
 * <br>
 * - {@code minScore}: The minimum relevance score for the returned {@link Content}s.
 * {@link Content}s scoring below {@code #minScore} are excluded from the results.
 * <br>
 * - {@code dynamicMinScore}: It is a {@link Function} that accepts a {@link Query} and returns a {@code minScore} value.
 * It can be used to dynamically define {@code minScore} value, depending on factors such as the query,
 * the user (using Metadata#chatMemoryId()} from {@link Query#metadata()}), etc.
 * <br>
 * - {@code filter}: The {@link Filter} that will be applied to a {@link dev.langchain4j.data.document.Metadata} in the
 * {@link Content#textSegment()}.
 * <br>
 * - {@code dynamicFilter}: It is a {@link Function} that accepts a {@link Query} and returns a {@code filter} value.
 * It can be used to dynamically define {@code filter} value, depending on factors such as the query,
 * the user (using Metadata#chatMemoryId()} from {@link Query#metadata()}), etc.
 * <br>
 * - {@code removeDuplicateOverlap}: When {@code true}, de-duplicates overlapping text between sequential
 * {@link TextSegment}s from the same document. This occurs when documents are split with overlap: adjacent segments
 * share a common suffix/prefix. Enabling this removes the duplicate prefix from the later segment before returning.
 * Default is {@code false}.
 */
public class EmbeddingStoreContentRetriever implements ContentRetriever {

    public static final Function<Query, Integer> DEFAULT_MAX_RESULTS = (query) -> 3;
    public static final Function<Query, Double> DEFAULT_MIN_SCORE = (query) -> 0.0;
    public static final Function<Query, Filter> DEFAULT_FILTER = (query) -> null;

    public static final String DEFAULT_DISPLAY_NAME = "Default";
    public static final boolean DEFAULT_REMOVE_DUPLICATE_OVERLAP = false;

    private static final String SEGMENT_INDEX_METADATA_KEY = "index";

    private final EmbeddingStore<TextSegment> embeddingStore;
    private final EmbeddingModel embeddingModel;

    private final Function<Query, Integer> maxResultsProvider;
    private final Function<Query, Double> minScoreProvider;
    private final Function<Query, Filter> filterProvider;

    private final String displayName;
    private final boolean removeDuplicateOverlap;

    public EmbeddingStoreContentRetriever(EmbeddingStore<TextSegment> embeddingStore, EmbeddingModel embeddingModel) {
        this(
                DEFAULT_DISPLAY_NAME,
                embeddingStore,
                embeddingModel,
                DEFAULT_MAX_RESULTS,
                DEFAULT_MIN_SCORE,
                DEFAULT_FILTER,
                DEFAULT_REMOVE_DUPLICATE_OVERLAP);
    }

    public EmbeddingStoreContentRetriever(
            EmbeddingStore<TextSegment> embeddingStore, EmbeddingModel embeddingModel, int maxResults) {
        this(
                DEFAULT_DISPLAY_NAME,
                embeddingStore,
                embeddingModel,
                (query) -> maxResults,
                DEFAULT_MIN_SCORE,
                DEFAULT_FILTER,
                DEFAULT_REMOVE_DUPLICATE_OVERLAP);
    }

    public EmbeddingStoreContentRetriever(
            EmbeddingStore<TextSegment> embeddingStore,
            EmbeddingModel embeddingModel,
            Integer maxResults,
            Double minScore) {
        this(
                DEFAULT_DISPLAY_NAME,
                embeddingStore,
                embeddingModel,
                (query) -> maxResults,
                (query) -> minScore,
                DEFAULT_FILTER,
                DEFAULT_REMOVE_DUPLICATE_OVERLAP);
    }

    private EmbeddingStoreContentRetriever(
            String displayName,
            EmbeddingStore<TextSegment> embeddingStore,
            EmbeddingModel embeddingModel,
            Function<Query, Integer> dynamicMaxResults,
            Function<Query, Double> dynamicMinScore,
            Function<Query, Filter> dynamicFilter,
            boolean removeDuplicateOverlap) {
        this.displayName = getOrDefault(displayName, DEFAULT_DISPLAY_NAME);
        this.embeddingStore = ensureNotNull(embeddingStore, "embeddingStore");
        this.embeddingModel = ensureNotNull(
                getOrDefault(embeddingModel, EmbeddingStoreContentRetriever::loadEmbeddingModel), "embeddingModel");
        this.maxResultsProvider = getOrDefault(dynamicMaxResults, DEFAULT_MAX_RESULTS);
        this.minScoreProvider = getOrDefault(dynamicMinScore, DEFAULT_MIN_SCORE);
        this.filterProvider = getOrDefault(dynamicFilter, DEFAULT_FILTER);
        this.removeDuplicateOverlap = removeDuplicateOverlap;
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

    public static EmbeddingStoreContentRetrieverBuilder builder() {
        return new EmbeddingStoreContentRetrieverBuilder();
    }

    public static class EmbeddingStoreContentRetrieverBuilder {

        private String displayName;
        private EmbeddingStore<TextSegment> embeddingStore;
        private EmbeddingModel embeddingModel;
        private Function<Query, Integer> dynamicMaxResults;
        private Function<Query, Double> dynamicMinScore;
        private Function<Query, Filter> dynamicFilter;
        private boolean removeDuplicateOverlap = DEFAULT_REMOVE_DUPLICATE_OVERLAP;

        EmbeddingStoreContentRetrieverBuilder() {}

        public EmbeddingStoreContentRetrieverBuilder maxResults(Integer maxResults) {
            if (maxResults != null) {
                dynamicMaxResults = (query) -> ensureGreaterThanZero(maxResults, "maxResults");
            }
            return this;
        }

        public EmbeddingStoreContentRetrieverBuilder minScore(Double minScore) {
            if (minScore != null) {
                dynamicMinScore = (query) -> ensureBetween(minScore, 0, 1, "minScore");
            }
            return this;
        }

        public EmbeddingStoreContentRetrieverBuilder filter(Filter filter) {
            if (filter != null) {
                dynamicFilter = (query) -> filter;
            }
            return this;
        }

        public EmbeddingStoreContentRetrieverBuilder displayName(String displayName) {
            this.displayName = displayName;
            return this;
        }

        public EmbeddingStoreContentRetrieverBuilder embeddingStore(EmbeddingStore<TextSegment> embeddingStore) {
            this.embeddingStore = embeddingStore;
            return this;
        }

        public EmbeddingStoreContentRetrieverBuilder embeddingModel(EmbeddingModel embeddingModel) {
            this.embeddingModel = embeddingModel;
            return this;
        }

        public EmbeddingStoreContentRetrieverBuilder dynamicMaxResults(Function<Query, Integer> dynamicMaxResults) {
            this.dynamicMaxResults = dynamicMaxResults;
            return this;
        }

        public EmbeddingStoreContentRetrieverBuilder dynamicMinScore(Function<Query, Double> dynamicMinScore) {
            this.dynamicMinScore = dynamicMinScore;
            return this;
        }

        public EmbeddingStoreContentRetrieverBuilder dynamicFilter(Function<Query, Filter> dynamicFilter) {
            this.dynamicFilter = dynamicFilter;
            return this;
        }

        /**
         * Whether to remove duplicate overlapping text between sequential {@link TextSegment}s from the same document.
         * <p>When documents are split with overlap, adjacent segments share a common suffix/prefix.
         * When {@code true} (default), this duplicate prefix is removed from the later segment before returning.</p>
         *
         * @param removeDuplicateOverlap {@code true} to remove duplicate overlap, {@code false} to keep it (default)
         * @return builder
         */
        public EmbeddingStoreContentRetrieverBuilder removeDuplicateOverlap(boolean removeDuplicateOverlap) {
            this.removeDuplicateOverlap = removeDuplicateOverlap;
            return this;
        }

        public EmbeddingStoreContentRetriever build() {
            return new EmbeddingStoreContentRetriever(
                    this.displayName,
                    this.embeddingStore,
                    this.embeddingModel,
                    this.dynamicMaxResults,
                    this.dynamicMinScore,
                    this.dynamicFilter,
                    this.removeDuplicateOverlap);
        }
    }

    /**
     * Creates an instance of an {@code EmbeddingStoreContentRetriever} from the specified {@link EmbeddingStore}
     * and {@link EmbeddingModel} found through SPI (see {@link EmbeddingModelFactory}).
     */
    public static EmbeddingStoreContentRetriever from(EmbeddingStore<TextSegment> embeddingStore) {
        return builder().embeddingStore(embeddingStore).build();
    }

    @Override
    public List<Content> retrieve(Query query) {

        Embedding embeddedQuery = embeddingModel.embed(query.text()).content();

        EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                .query(query.text())
                .queryEmbedding(embeddedQuery)
                .maxResults(maxResultsProvider.apply(query))
                .minScore(minScoreProvider.apply(query))
                .filter(filterProvider.apply(query))
                .build();

        EmbeddingSearchResult<TextSegment> searchResult = embeddingStore.search(searchRequest);

        List<Content> contents = searchResult.matches().stream()
                .map(embeddingMatch -> Content.from(
                        embeddingMatch.embedded(),
                        Map.of(
                                ContentMetadata.SCORE, embeddingMatch.score(),
                                ContentMetadata.EMBEDDING_ID, embeddingMatch.embeddingId())))
                .collect(Collectors.toList());

        if (removeDuplicateOverlap) {
            contents = deduplicateOverlap(contents);
        }

        return contents;
    }

    /**
     * Removes duplicate overlapping text between sequential {@link TextSegment}s from the same document.
     *
     * <p>When documents are split with overlap, segment N's tail content is repeated at segment N+1's head.
     * This method finds pairs of retrieved segments that are adjacent in the original document
     * (same document metadata, consecutive "index" values) and removes the repeated prefix from the later segment.
     *
     * <p>Document identity is determined by all metadata keys except "index".
     * Sequential order is determined by the "index" metadata key set by document splitters.
     */
    private static List<Content> deduplicateOverlap(List<Content> contents) {
        // Map from (docKey -> (segmentIndex -> Content)) to find adjacent pairs
        Map<Map<String, Object>, Map<Integer, Content>> byDocument = new HashMap<>();

        for (Content content : contents) {
            Integer segmentIndex = content.textSegment().metadata().getInteger(SEGMENT_INDEX_METADATA_KEY);
            if (segmentIndex == null) {
                continue;
            }

            Map<String, Object> docKey =
                    new HashMap<>(content.textSegment().metadata().toMap());
            docKey.remove(SEGMENT_INDEX_METADATA_KEY);

            byDocument.computeIfAbsent(docKey, k -> new HashMap<>()).put(segmentIndex, content);
        }

        // Build a set of Content objects whose text prefix should be trimmed
        Map<Content, String> trimmedTexts = new HashMap<>();

        for (Map<Integer, Content> segmentsByIndex : byDocument.values()) {
            List<Integer> sortedIndices = segmentsByIndex.keySet().stream()
                    .sorted(Comparator.naturalOrder())
                    .collect(Collectors.toList());

            for (int i = 0; i < sortedIndices.size() - 1; i++) {
                int currentIndex = sortedIndices.get(i);
                int nextIndex = sortedIndices.get(i + 1);

                if (nextIndex != currentIndex + 1) {
                    continue;
                }

                Content current = segmentsByIndex.get(currentIndex);
                Content next = segmentsByIndex.get(nextIndex);

                String currentText = current.textSegment().text();
                String nextText = next.textSegment().text();

                String overlap = longestSuffixPrefixOverlap(currentText, nextText);
                if (!overlap.isEmpty()) {
                    trimmedTexts.put(next, nextText.substring(overlap.length()));
                }
            }
        }

        if (trimmedTexts.isEmpty()) {
            return contents;
        }

        List<Content> result = new ArrayList<>(contents.size());
        for (Content content : contents) {
            String trimmedText = trimmedTexts.get(content);
            if (trimmedText != null) {
                TextSegment trimmed =
                        TextSegment.from(trimmedText, content.textSegment().metadata());
                result.add(Content.from(trimmed, content.metadata()));
            } else {
                result.add(content);
            }
        }
        return result;
    }

    /**
     * Returns the longest string that is both a suffix of {@code a} and a prefix of {@code b}.
     */
    private static String longestSuffixPrefixOverlap(String a, String b) {
        int maxLen = Math.min(a.length(), b.length());
        for (int len = maxLen; len > 0; len--) {
            if (a.endsWith(b.substring(0, len))) {
                return b.substring(0, len);
            }
        }
        return "";
    }

    @Override
    public String toString() {
        return "EmbeddingStoreContentRetriever{" + "displayName='" + displayName + '\'' + '}';
    }
}
