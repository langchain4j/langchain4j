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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
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
 * - {@code removeDuplicateOverlap}: When documents are split with overlap, adjacent retrieved {@link TextSegment}s
 * from the same document share a repeated suffix/prefix. When {@code true} (default), this duplicate overlap
 * text is detected and removed from the start of the later segment, preventing the LLM from receiving redundant
 * content. Set to {@code false} to disable this behaviour and return segments as-is.
 */
public class EmbeddingStoreContentRetriever implements ContentRetriever {

    static final String INDEX_METADATA_KEY = "index";

    public static final Function<Query, Integer> DEFAULT_MAX_RESULTS = (query) -> 3;
    public static final Function<Query, Double> DEFAULT_MIN_SCORE = (query) -> 0.0;
    public static final Function<Query, Filter> DEFAULT_FILTER = (query) -> null;
    public static final boolean DEFAULT_REMOVE_DUPLICATE_OVERLAP = true;

    public static final String DEFAULT_DISPLAY_NAME = "Default";

    private final EmbeddingStore<TextSegment> embeddingStore;
    private final EmbeddingModel embeddingModel;

    private final Function<Query, Integer> maxResultsProvider;
    private final Function<Query, Double> minScoreProvider;
    private final Function<Query, Filter> filterProvider;
    private final boolean removeDuplicateOverlap;

    private final String displayName;

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
                maxResults != null ? (query) -> maxResults : DEFAULT_MAX_RESULTS,
                minScore != null ? (query) -> minScore : DEFAULT_MIN_SCORE,
                DEFAULT_FILTER,
                DEFAULT_REMOVE_DUPLICATE_OVERLAP);
    }

    public EmbeddingStoreContentRetriever(
            String displayName,
            EmbeddingStore<TextSegment> embeddingStore,
            EmbeddingModel embeddingModel,
            Function<Query, Integer> dynamicMaxResults,
            Function<Query, Double> dynamicMinScore,
            Function<Query, Filter> dynamicFilter) {
        this(
                displayName,
                embeddingStore,
                embeddingModel,
                dynamicMaxResults,
                dynamicMinScore,
                dynamicFilter,
                DEFAULT_REMOVE_DUPLICATE_OVERLAP);
    }

    public EmbeddingStoreContentRetriever(
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
        private Boolean removeDuplicateOverlap;

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
         * When documents are split with overlap, adjacent retrieved {@link TextSegment}s from the same document
         * share a repeated suffix/prefix. When set to {@code true} (default), this duplicate overlap text is
         * detected and removed from the start of the later segment, preventing the LLM from receiving redundant
         * content. Set to {@code false} to disable this behaviour and return segments as-is.
         *
         * @param removeDuplicateOverlap whether to remove duplicate overlap (default: true)
         * @return this builder
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
                    this.removeDuplicateOverlap != null
                            ? this.removeDuplicateOverlap
                            : DEFAULT_REMOVE_DUPLICATE_OVERLAP);
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
     * Removes duplicate overlap text from sequential {@link TextSegment}s retrieved from the same document.
     *
     * <p>When a document is split with overlap, adjacent segments share a repeated suffix/prefix.
     * This method groups retrieved contents by document identity (all metadata except {@code "index"}),
     * then for each pair of consecutive segments (index N and N+1), finds the longest common
     * suffix-prefix overlap and removes it from the start of the later segment.
     *
     * <p>Segments without an {@code "index"} metadata key, or that are non-adjacent, are left unchanged.
     *
     * @param contents the list of retrieved contents
     * @return a new list with duplicate overlap removed from sequential segments
     */
    static List<Content> deduplicateOverlap(List<Content> contents) {
        if (contents.size() < 2) {
            return contents;
        }

        // Group contents by document identity: all metadata except "index"
        Map<Map<String, Object>, TreeMap<Integer, Content>> docGroups = new HashMap<>();

        for (Content content : contents) {
            TextSegment segment = content.textSegment();
            String indexStr = segment.metadata().getString(INDEX_METADATA_KEY);
            if (indexStr == null) {
                continue;
            }
            int index;
            try {
                index = Integer.parseInt(indexStr);
            } catch (NumberFormatException e) {
                continue;
            }

            // Document identity = all metadata except "index"
            Map<String, Object> docId = new HashMap<>(segment.metadata().toMap());
            docId.remove(INDEX_METADATA_KEY);

            docGroups.computeIfAbsent(docId, k -> new TreeMap<>()).put(index, content);
        }

        // Build replacement map: original Content -> deduplicated Content
        Map<Content, Content> replacements = new HashMap<>();

        for (TreeMap<Integer, Content> indexedContents : docGroups.values()) {
            List<Map.Entry<Integer, Content>> entries = new ArrayList<>(indexedContents.entrySet());
            for (int i = 0; i < entries.size() - 1; i++) {
                Map.Entry<Integer, Content> curr = entries.get(i);
                Map.Entry<Integer, Content> next = entries.get(i + 1);

                // Only process truly adjacent segments (index N and N+1)
                if (next.getKey() != curr.getKey() + 1) {
                    continue;
                }

                String currText = curr.getValue().textSegment().text();
                String nextText = next.getValue().textSegment().text();

                int overlapLen = longestSuffixPrefixOverlap(currText, nextText);
                if (overlapLen > 0) {
                    String deduplicated = nextText.substring(overlapLen);
                    if (deduplicated.isBlank()) {
                        continue; // do not produce a blank segment
                    }
                    TextSegment newSegment = TextSegment.from(
                            deduplicated, next.getValue().textSegment().metadata());
                    Content newContent = Content.from(newSegment, next.getValue().metadata());
                    replacements.put(next.getValue(), newContent);
                }
            }
        }

        if (replacements.isEmpty()) {
            return contents;
        }

        return contents.stream()
                .map(c -> replacements.getOrDefault(c, c))
                .collect(Collectors.toList());
    }

    /**
     * Finds the length of the longest suffix of {@code a} that is also a prefix of {@code b}.
     *
     * <p>For example:
     * <pre>
     *   a = "the quick brown fox"
     *   b = "brown fox jumps over"
     *   result = 9  ("brown fox")
     * </pre>
     *
     * @param a the earlier segment text
     * @param b the later segment text
     * @return the length of the longest overlapping suffix/prefix, or 0 if none
     */
    static int longestSuffixPrefixOverlap(String a, String b) {
        int maxOverlap = Math.min(a.length(), b.length());
        for (int len = maxOverlap; len > 0; len--) {
            if (a.regionMatches(a.length() - len, b, 0, len)) {
                return len;
            }
        }
        return 0;
    }

    @Override
    public String toString() {
        return "EmbeddingStoreContentRetriever{" + "displayName='" + displayName + '\'' + '}';
    }
}
