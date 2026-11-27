package dev.langchain4j.rag.content.retriever;

import static dev.langchain4j.internal.CompletableFutureUtils.failOnThrow;
import static dev.langchain4j.internal.CompletableFutureUtils.propagateCancellation;
import static dev.langchain4j.internal.Exceptions.unwrapCompletionException;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureBetween;
import static dev.langchain4j.internal.ValidationUtils.ensureGreaterThanZero;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static dev.langchain4j.spi.ServiceHelper.loadFactories;

import dev.langchain4j.Experimental;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.exception.AsyncNotSupportedException;
import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.internal.CancellationChain;
import dev.langchain4j.internal.CurrentInvocation;
import dev.langchain4j.internal.DefaultExecutorProvider;
import dev.langchain4j.invocation.CapturedContextSupport;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.model.TokenCountEstimator;
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
import java.util.ArrayList;
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
 * <br>
 * - {@code maxTokens}: The maximum total number of tokens in the retrieved {@link Content}s,
 * as counted by a {@link TokenCountEstimator}. {@link Content}s are kept in order of relevance while their total fits.
 * At most {@code maxResults} {@link Content}s are retrieved, so {@code maxResults} should be raised
 * for the token limit to take effect.
 * <br>
 * - {@code dynamicMaxTokens}: It is a {@link Function} that accepts a {@link Query} and returns a {@code maxTokens} value.
 * It can be used to dynamically define {@code maxTokens} value, depending on factors such as the query,
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

    private final Function<Query, Integer> maxTokensProvider;
    private final TokenCountEstimator tokenCountEstimator;

    private final EmbeddingInputType embeddingInputType;

    private final String displayName;

    // When the embedding model or store is not genuinely async (its doEmbedAsync/searchAsync throws), retrieveAsync
    // fails loudly by default (surfacing that exception) rather than silently offloading it. Set offloadBlocking(true)
    // to instead offload only the blocking component to the shared default executor (virtual threads on Java 21+,
    // platform threads on 17-20).
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
                false,
                null,
                null);
    }

    private EmbeddingStoreContentRetriever(
            String displayName,
            EmbeddingStore<TextSegment> embeddingStore,
            EmbeddingModel embeddingModel,
            Function<Query, Integer> dynamicMaxResults,
            Function<Query, Double> dynamicMinScore,
            Function<Query, Filter> dynamicFilter,
            EmbeddingInputType embeddingInputType,
            boolean offloadBlocking,
            Function<Query, Integer> dynamicMaxTokens,
            TokenCountEstimator tokenCountEstimator) {
        this.displayName = getOrDefault(displayName, DEFAULT_DISPLAY_NAME);
        this.embeddingStore = ensureNotNull(embeddingStore, "embeddingStore");
        this.embeddingModel = ensureNotNull(
                getOrDefault(embeddingModel, EmbeddingStoreContentRetriever::loadEmbeddingModel), "embeddingModel");
        this.maxResultsProvider = getOrDefault(dynamicMaxResults, DEFAULT_MAX_RESULTS);
        this.minScoreProvider = getOrDefault(dynamicMinScore, DEFAULT_MIN_SCORE);
        this.filterProvider = getOrDefault(dynamicFilter, DEFAULT_FILTER);
        this.embeddingInputType = embeddingInputType;
        this.offloadBlocking = offloadBlocking;
        this.maxTokensProvider = dynamicMaxTokens;
        this.tokenCountEstimator =
                dynamicMaxTokens == null ? null : ensureNotNull(tokenCountEstimator, "tokenCountEstimator");
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
        private Function<Query, Integer> dynamicMaxTokens;
        private TokenCountEstimator tokenCountEstimator;

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
         * Sets a token budget for the retrieved {@link Content}s.
         * <p>
         * {@link Content}s are kept in order, most relevant first, while the total number of tokens in their text
         * fits into {@code maxTokens}. The first {@link Content} that does not fit, and all less relevant ones,
         * are excluded. If the most relevant {@link Content} alone does not fit, no {@link Content}s are returned.
         * <p>
         * The budget applies to the (at most {@code maxResults}) {@link Content}s found in the store,
         * so set {@code maxResults} high enough for the budget to take effect.
         *
         * @param maxTokens           the maximum total number of tokens; {@code null} means no limit
         * @param tokenCountEstimator counts the tokens in the text of each {@link Content};
         *                            required when {@code maxTokens} is set
         * @return builder
         * @since 1.25.0
         */
        public EmbeddingStoreContentRetrieverBuilder maxTokens(
                Integer maxTokens, TokenCountEstimator tokenCountEstimator) {
            if (maxTokens != null) {
                ensureGreaterThanZero(maxTokens, "maxTokens");
                this.dynamicMaxTokens = (query) -> maxTokens;
                this.tokenCountEstimator = tokenCountEstimator;
            }
            return this;
        }

        /**
         * Same as {@link #maxTokens(Integer, TokenCountEstimator)}, but the budget is chosen per {@link Query}.
         * When the function returns {@code null} for a query, no budget is applied to that query.
         *
         * @param dynamicMaxTokens    returns the maximum total number of tokens for a query
         * @param tokenCountEstimator counts the tokens in the text of each {@link Content}
         * @return builder
         * @since 1.25.0
         */
        public EmbeddingStoreContentRetrieverBuilder dynamicMaxTokens(
                Function<Query, Integer> dynamicMaxTokens, TokenCountEstimator tokenCountEstimator) {
            this.dynamicMaxTokens = dynamicMaxTokens;
            this.tokenCountEstimator = tokenCountEstimator;
            return this;
        }

        /**
         * Controls what {@link #retrieveAsync(Query)} does when the {@link EmbeddingModel} or {@link EmbeddingStore}
         * is not genuinely asynchronous (does not implement {@code doEmbedAsync}/{@code searchAsync}).
         * <p>
         * By default ({@code false}), {@code retrieveAsync} fails with an {@link UnsupportedFeatureException} naming
         * the blocking component, so a not-truly-async pipeline is never silently made "async" by parking a thread.
         * Set to {@code true} to instead offload <i>only the blocking component(s)</i> - the embedding call and/or the
         * store search - to the shared default executor (virtual threads on Java 21+, platform threads on 17-20), while any async component keeps running on its native
         * path. A deliberate opt-in to blocking-on-a-(virtual)-thread; has no effect on the synchronous
         * {@link #retrieve(Query)}.
         * @since 1.20.0
         */
        @Experimental
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
                    this.offloadBlocking,
                    this.dynamicMaxTokens,
                    this.tokenCountEstimator);
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

        return fitIntoTokenBudget(toContents(searchResult), query);
    }

    /**
     * Non-blocking counterpart of {@link #retrieve(Query)} that never blocks the calling thread.
     * <p>
     * The query embedding and the vector-store search each run on their component's native async path
     * ({@link EmbeddingModel#embedAsync(EmbeddingRequest)} / {@link EmbeddingStore#searchAsync(EmbeddingSearchRequest)}),
     * so a genuinely async component runs asynchronously even when the other one is blocking (e.g. an async embedding
     * model with a blocking vector store).
     * <p>
     * If a component is blocking (its async method is not implemented), the returned future fails with an
     * {@link UnsupportedFeatureException} - the pipeline is not silently made "async" by parking a thread. Building
     * the retriever with {@code offloadBlocking(true)} instead offloads <i>only the blocking component</i> to a shared
     * default executor (virtual threads on Java 21+, platform threads on 17-20), leaving any async component on its native path.
     */
    @Override
    public CompletableFuture<List<Content>> retrieveAsync(Query query) {
        CompletableFuture<List<Content>> result = new CompletableFuture<>();
        CancellationChain chain = new CancellationChain(result);
        chain.track(nativeOrOffload(query, () -> embedQueryAsync(query.text()), () -> embedQuery(query.text())))
                .thenCompose(embedding -> {
                    EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                            .query(query.text())
                            .queryEmbedding(embedding)
                            .maxResults(maxResultsProvider.apply(query))
                            .minScore(minScoreProvider.apply(query))
                            .filter(filterProvider.apply(query))
                            .build();
                    return chain.track(nativeOrOffload(
                            query,
                            () -> embeddingStore.searchAsync(searchRequest),
                            () -> embeddingStore.search(searchRequest)));
                })
                .thenApply(searchResult -> fitIntoTokenBudget(toContents(searchResult), query))
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
     * via an {@link AsyncNotSupportedException} - then either offload the corresponding blocking call to a shared
     * default executor (virtual threads on Java 21+, platform threads on 17-20) (when {@code offloadBlocking}) or fail with an actionable message. Any other error
     * propagates unchanged. No reflection: the (un)availability of async is discovered by calling it.
     */
    private <T> CompletableFuture<T> nativeOrOffload(
            Query query, Supplier<CompletableFuture<T>> asyncCall, Supplier<T> blockingCall) {
        CompletableFuture<T> async = failOnThrow(asyncCall);
        CompletableFuture<T> result = async.exceptionallyCompose(error -> {
            Throwable cause = unwrapCompletionException(error);
            if (cause instanceof AsyncNotSupportedException) {
                if (offloadBlocking) {
                    InvocationContext invocationContext =
                            query.metadata() != null ? query.metadata().invocationContext() : null;
                    return CompletableFuture.supplyAsync(
                            () -> CurrentInvocation.callWith(invocationContext, blockingCall),
                            CapturedContextSupport.restoringIn(
                                    DefaultExecutorProvider.getDefaultExecutor(), invocationContext));
                }
                return CompletableFuture.failedFuture(
                        new UnsupportedFeatureException(
                                cause.getMessage()
                                        + " Build the retriever with EmbeddingStoreContentRetriever.builder().offloadBlocking(true)"
                                        + " to offload this blocking component to a default executor (virtual threads on Java 21+, platform threads on 17-20) instead."));
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
        return embeddingModel
                .embedAsync(builder.build())
                .thenApply(response -> response.embeddings().get(0));
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

    /**
     * Keeps {@link Content}s in the given order (most relevant first) while their total token count fits into
     * the {@code maxTokens} for the given query. Stops at the first {@link Content} that does not fit,
     * so a less relevant {@link Content} is never returned in place of a more relevant one.
     */
    private List<Content> fitIntoTokenBudget(List<Content> contents, Query query) {
        if (maxTokensProvider == null) {
            return contents;
        }
        Integer maxTokens = maxTokensProvider.apply(query);
        if (maxTokens == null) {
            return contents;
        }
        ensureGreaterThanZero(maxTokens, "maxTokens");

        List<Content> fitting = new ArrayList<>();
        int usedTokens = 0;
        for (Content content : contents) {
            usedTokens += tokenCountEstimator.estimateTokenCountInText(
                    content.textSegment().text());
            if (usedTokens > maxTokens) {
                break;
            }
            fitting.add(content);
        }
        return fitting;
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
