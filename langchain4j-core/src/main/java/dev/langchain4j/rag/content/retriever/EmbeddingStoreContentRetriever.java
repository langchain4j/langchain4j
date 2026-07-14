package dev.langchain4j.rag.content.retriever;

import dev.langchain4j.exception.AsyncNotSupportedException;
import static dev.langchain4j.internal.CompletableFutureUtils.propagateCancellation;
import static dev.langchain4j.internal.Exceptions.unwrapCompletionException;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureBetween;
import static dev.langchain4j.internal.ValidationUtils.ensureGreaterThanZero;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static dev.langchain4j.spi.ServiceHelper.loadFactories;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.internal.CancellationChain;
import dev.langchain4j.internal.DefaultExecutorProvider;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.request.EmbeddingInputType;
import dev.langchain4j.model.embedding.request.EmbeddingRequest;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.ContentMetadata;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.spi.model.embedding.EmbeddingModelFactory;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Supplier;
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
 */
public class EmbeddingStoreContentRetriever implements ContentRetriever {

    public static final Function<Query, Integer> DEFAULT_MAX_RESULTS = (query) -> 3;
    public static final Function<Query, Double> DEFAULT_MIN_SCORE = (query) -> 0.0;
    public static final Function<Query, Filter> DEFAULT_FILTER = (query) -> null;

    public static final String DEFAULT_DISPLAY_NAME = "Default";

    private final EmbeddingStore<TextSegment> embeddingStore;
    private final EmbeddingModel embeddingModel;

    private final Function<Query, Integer> maxResultsProvider;
    private final Function<Query, Double> minScoreProvider;
    private final Function<Query, Filter> filterProvider;

    private final EmbeddingInputType embeddingInputType;

    private final String displayName;

    // When the embedding model or store is not genuinely async (its doEmbedAsync/searchAsync throws), retrieveAsync
    // fails loudly by default (surfacing that exception) rather than silently offloading it. Set offloadBlocking(true)
    // to instead offload only the blocking component to a shared virtual-thread executor.
    private final boolean offloadBlocking;

    public EmbeddingStoreContentRetriever(EmbeddingStore<TextSegment> embeddingStore, EmbeddingModel embeddingModel) {
        this(
                DEFAULT_DISPLAY_NAME,
                embeddingStore,
                embeddingModel,
                DEFAULT_MAX_RESULTS,
                DEFAULT_MIN_SCORE,
                DEFAULT_FILTER,
                null);
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
                null);
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
                null);
    }

    private EmbeddingStoreContentRetriever(
            String displayName,
            EmbeddingStore<TextSegment> embeddingStore,
            EmbeddingModel embeddingModel,
            Function<Query, Integer> dynamicMaxResults,
            Function<Query, Double> dynamicMinScore,
            Function<Query, Filter> dynamicFilter,
            EmbeddingInputType embeddingInputType) {
        this(
                displayName,
                embeddingStore,
                embeddingModel,
                dynamicMaxResults,
                dynamicMinScore,
                dynamicFilter,
                embeddingInputType,
                false);
    }

    private EmbeddingStoreContentRetriever(
            String displayName,
            EmbeddingStore<TextSegment> embeddingStore,
            EmbeddingModel embeddingModel,
            Function<Query, Integer> dynamicMaxResults,
            Function<Query, Double> dynamicMinScore,
            Function<Query, Filter> dynamicFilter,
            EmbeddingInputType embeddingInputType,
            boolean offloadBlocking) {
        this.displayName = getOrDefault(displayName, DEFAULT_DISPLAY_NAME);
        this.embeddingStore = ensureNotNull(embeddingStore, "embeddingStore");
        this.embeddingModel = ensureNotNull(
                getOrDefault(embeddingModel, EmbeddingStoreContentRetriever::loadEmbeddingModel), "embeddingModel");
        this.maxResultsProvider = getOrDefault(dynamicMaxResults, DEFAULT_MAX_RESULTS);
        this.minScoreProvider = getOrDefault(dynamicMinScore, DEFAULT_MIN_SCORE);
        this.filterProvider = getOrDefault(dynamicFilter, DEFAULT_FILTER);
        this.embeddingInputType = embeddingInputType;
        this.offloadBlocking = offloadBlocking;
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
        private EmbeddingInputType embeddingInputType;
        private boolean offloadBlocking;

        EmbeddingStoreContentRetrieverBuilder() {}

        /**
         * Embeds the query with the given {@link EmbeddingInputType} (typically {@link EmbeddingInputType#QUERY}),
         * so providers that encode queries and documents differently can produce a query-optimized embedding.
         * <p>
         * When left {@code null} (the default), no input type is sent. If set, the chosen {@link EmbeddingModel}
         * must {@link EmbeddingModel#supportedParameters() support} the input type parameter, otherwise the query
         * embedding fails fast.
         */
        public EmbeddingStoreContentRetrieverBuilder embeddingInputType(EmbeddingInputType embeddingInputType) {
            this.embeddingInputType = embeddingInputType;
            return this;
        }

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
         * Controls what {@link #retrieveAsync(Query)} does when the {@link EmbeddingModel} or {@link EmbeddingStore}
         * is not genuinely asynchronous (does not implement {@code doEmbedAsync}/{@code searchAsync}).
         * <p>
         * By default ({@code false}), {@code retrieveAsync} fails with an {@link UnsupportedFeatureException} naming
         * the blocking component, so a not-truly-async pipeline is never silently made "async" by parking a thread.
         * Set to {@code true} to instead offload <i>only the blocking component(s)</i> - the embedding call and/or the
         * store search - to a shared virtual-thread executor, while any async component keeps running on its native
         * path. A deliberate opt-in to blocking-on-a-(virtual)-thread; has no effect on the synchronous
         * {@link #retrieve(Query)}.
         */
        public EmbeddingStoreContentRetrieverBuilder offloadBlocking(boolean offloadBlocking) {
            this.offloadBlocking = offloadBlocking;
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
                    this.embeddingInputType,
                    this.offloadBlocking);
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

        Embedding embeddedQuery = embedQuery(query.text());

        EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                .query(query.text())
                .queryEmbedding(embeddedQuery)
                .maxResults(maxResultsProvider.apply(query))
                .minScore(minScoreProvider.apply(query))
                .filter(filterProvider.apply(query))
                .build();

        EmbeddingSearchResult<TextSegment> searchResult = embeddingStore.search(searchRequest);

        return toContents(searchResult);
    }

    /**
     * Non-blocking counterpart of {@link #retrieve(Query)} that never blocks the calling thread.
     * <p>
     * The query embedding and the vector-store search each run on their component's native async path
     * ({@link EmbeddingModel#embedAsync(EmbeddingRequest)} / {@link EmbeddingStore#searchAsync(EmbeddingSearchRequest)}),
     * so a genuinely async component runs asynchronously even when the other one is blocking (e.g. an async embedding
     * model with a blocking vector store).
     * <p>
     * If a component is blocking (its async method is not implemented), the returned future fails with that
     * {@link UnsupportedOperationException} - the pipeline is not silently made "async" by parking a thread. Building
     * the retriever with {@code offloadBlocking(true)} instead offloads <i>only the blocking component</i> to a shared
     * virtual-thread executor, leaving any async component on its native path.
     */
    @Override
    public CompletableFuture<List<Content>> retrieveAsync(Query query) {
        // Root a cancellation chain at the caller-facing future so cancelling it aborts whichever hop is in flight -
        // the query embedding or the vector-store search - not just the outer composed stage.
        CompletableFuture<List<Content>> result = new CompletableFuture<>();
        CancellationChain chain = new CancellationChain(result);
        chain.track(nativeOrOffload(() -> embedQueryAsync(query.text()), () -> embedQuery(query.text())))
                .thenCompose(embedding -> {
                    EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                            .query(query.text())
                            .queryEmbedding(embedding)
                            .maxResults(maxResultsProvider.apply(query))
                            .minScore(minScoreProvider.apply(query))
                            .filter(filterProvider.apply(query))
                            .build();
                    return chain.track(nativeOrOffload(
                            () -> embeddingStore.searchAsync(searchRequest),
                            () -> embeddingStore.search(searchRequest)));
                })
                .thenApply(this::toContents)
                .whenComplete((contents, error) -> {
                    if (error != null) {
                        result.completeExceptionally(unwrapCompletionException(error));
                    } else {
                        result.complete(contents);
                    }
                });
        return result;
    }

    /**
     * Runs {@code asyncCall} (a component's async method). If it is blocking - i.e. it reports being unimplemented
     * via an {@link UnsupportedOperationException} - then either offload the corresponding blocking call to a shared
     * virtual-thread executor (when {@code offloadBlocking}) or fail with an actionable message. Any other error
     * propagates unchanged. No reflection: the (un)availability of async is discovered by calling it.
     */
    private <T> CompletableFuture<T> nativeOrOffload(Supplier<CompletableFuture<T>> asyncCall, Supplier<T> blockingCall) {
        CompletableFuture<T> async;
        try {
            async = asyncCall.get();
        } catch (Throwable t) {
            async = CompletableFuture.failedFuture(t);
        }
        CompletableFuture<T> result = async.exceptionallyCompose(error -> {
            Throwable cause = unwrapCompletionException(error);
            if (cause instanceof AsyncNotSupportedException) {
                if (offloadBlocking) {
                    return CompletableFuture.supplyAsync(
                            blockingCall, DefaultExecutorProvider.getDefaultExecutorService());
                }
                return CompletableFuture.failedFuture(new UnsupportedFeatureException(cause.getMessage()
                        + " Build the retriever with EmbeddingStoreContentRetriever.builder().offloadBlocking(true)"
                        + " to offload this blocking component to a virtual-thread executor instead."));
            }
            return CompletableFuture.failedFuture(error);
        });
        // Link the caller-facing derived stage back to the raw call so cancellation reaches the in-flight I/O.
        propagateCancellation(result, async);
        return result;
    }

    private CompletableFuture<Embedding> embedQueryAsync(String text) {
        EmbeddingRequest.Builder builder = EmbeddingRequest.builder().input(text);
        if (embeddingInputType != null) {
            builder.inputType(embeddingInputType);
        }
        return embeddingModel.embedAsync(builder.build()).thenApply(response -> response.embeddings().get(0));
    }

    private List<Content> toContents(EmbeddingSearchResult<TextSegment> searchResult) {
        return searchResult.matches().stream()
                .map(embeddingMatch -> Content.from(
                        embeddingMatch.embedded(),
                        Map.of(
                                ContentMetadata.SCORE, embeddingMatch.score(),
                                ContentMetadata.EMBEDDING_ID, embeddingMatch.embeddingId())))
                .collect(Collectors.toList());
    }

    private Embedding embedQuery(String text) {
        if (embeddingInputType == null) {
            return embeddingModel.embed(text).content();
        }
        return embeddingModel
                .embed(EmbeddingRequest.builder()
                        .input(text)
                        .inputType(embeddingInputType)
                        .build())
                .embeddings()
                .get(0);
    }

    @Override
    public String toString() {
        return "EmbeddingStoreContentRetriever{" + "displayName='" + displayName + '\'' + '}';
    }
}
