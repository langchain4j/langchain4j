package dev.langchain4j.rag;

import dev.langchain4j.exception.AsyncNotSupportedException;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.internal.CancellationChain;
import dev.langchain4j.internal.DefaultExecutorProvider;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.aggregator.ContentAggregator;
import dev.langchain4j.rag.content.aggregator.DefaultContentAggregator;
import dev.langchain4j.rag.content.injector.ContentInjector;
import dev.langchain4j.rag.content.injector.DefaultContentInjector;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.rag.query.router.DefaultQueryRouter;
import dev.langchain4j.rag.query.router.QueryRouter;
import dev.langchain4j.rag.query.transformer.DefaultQueryTransformer;
import dev.langchain4j.rag.query.transformer.QueryTransformer;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static dev.langchain4j.internal.CompletableFutureUtils.propagateCancellation;
import static dev.langchain4j.internal.Exceptions.unwrapCompletionException;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static java.util.concurrent.CompletableFuture.allOf;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.concurrent.CompletableFuture.supplyAsync;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.stream.Collectors.toMap;

/**
 * The default implementation of {@link RetrievalAugmentor} intended to be suitable for the majority of use cases.
 * <br>
 * <br>
 * It's important to note that while efforts will be made to avoid breaking changes,
 * the default behavior of this class may be updated in the future if it's found
 * that the current behavior does not adequately serve the majority of use cases.
 * Such changes would be made to benefit both current and future users.
 * <br>
 * <br>
 * This implementation is inspired by <a href="https://blog.langchain.dev/deconstructing-rag">this article</a>
 * and <a href="https://arxiv.org/abs/2312.10997">this paper</a>.
 * It is recommended to review these resources for a better understanding of the concept.
 * <br>
 * <br>
 * This implementation orchestrates the flow between the following base components:
 * <pre>
 * - {@link QueryTransformer}
 * - {@link QueryRouter}
 * - {@link ContentRetriever}
 * - {@link ContentAggregator}
 * - {@link ContentInjector}
 * </pre>
 * Visual representation of this flow can be found
 * <a href="https://docs.langchain4j.dev/img/advanced-rag.png">here</a>.
 * <br>
 * For each base component listed above, we offer several ready-to-use implementations,
 * each based on a recognized approach.
 * We intend to introduce more such implementations over time and welcome your contributions.
 * <br>
 * <br>
 * The flow is as follows:
 * <br>
 * 1. A {@link Query} (derived from an original {@link UserMessage}) is transformed
 * using a {@link QueryTransformer} into one or multiple {@link Query}s.
 * <br>
 * 2. Each {@link Query} is routed to the appropriate {@link ContentRetriever} using a {@link QueryRouter}.
 * Each {@link ContentRetriever} retrieves one or multiple {@link Content}s using a {@link Query}.
 * <br>
 * 3. All {@link Content}s retrieved by all {@link ContentRetriever}s using all {@link Query}s are
 * aggregated (fused/re-ranked/filtered/etc.) into a final list of {@link Content}s using a {@link ContentAggregator}.
 * <br>
 * 4. Lastly, a final list of {@link Content}s is injected into the original {@link UserMessage}
 * using a {@link ContentInjector}.
 * <br>
 * <br>
 * By default, each base component (except for {@link ContentRetriever}, which needs to be provided by you)
 * is initialized with a sensible default implementation:
 * <pre>
 * - {@link DefaultQueryTransformer}
 * - {@link DefaultQueryRouter}
 * - {@link DefaultContentAggregator}
 * - {@link DefaultContentInjector}
 * </pre>
 * Nonetheless, you are encouraged to use one of the advanced ready-to-use implementations or create a custom one.
 * <br>
 * <br>
 * When there is only a single {@link Query} and a single {@link ContentRetriever},
 * query routing and content retrieval are performed in the same thread.
 * Otherwise, an {@link Executor} is used to parallelize the processing.
 * By default, a modified (keepAliveTime is 1 second instead of 60 seconds) {@link Executors#newCachedThreadPool()}
 * is used, but you can provide a custom {@link Executor} instance.
 * <br>
 * On the asynchronous {@code augmentAsync} path, a blocking stage that is offloaded (see {@code offloadBlocking})
 * runs on the shared virtual-thread executor by default (non-pinning for I/O); a custom {@link Executor}, if
 * provided, is used there too.
 *
 * @see DefaultQueryTransformer
 * @see DefaultQueryRouter
 * @see DefaultContentAggregator
 * @see DefaultContentInjector
 */
public class DefaultRetrievalAugmentor implements RetrievalAugmentor {

    private final QueryTransformer queryTransformer;
    private final QueryRouter queryRouter;
    private final ContentAggregator contentAggregator;
    private final ContentInjector contentInjector;
    // Executor for the (synchronous) parallel routing/retrieval fan-out. Released default: a cached platform-thread
    // pool (a modified Executors.newCachedThreadPool()). Left unchanged for backward compatibility.
    private final Executor executor;
    // Executor for the async offload of a blocking stage (augmentAsync + offloadBlocking). Defaults to the shared
    // virtual-thread executor (non-pinning for I/O, consistent with EmbeddingStoreContentRetriever) but honors a
    // configured executor(...) - e.g. a platform pool for a CPU-bound blocking component.
    private final Executor asyncOffloadExecutor;
    // On augmentAsync, whether a stage that is not genuinely async (its *Async default throws) is offloaded to the
    // async offload executor, rather than failing loudly. See the builder's offloadBlocking(boolean).
    private final boolean offloadBlocking;

    public DefaultRetrievalAugmentor(QueryTransformer queryTransformer,
                                     QueryRouter queryRouter,
                                     ContentAggregator contentAggregator,
                                     ContentInjector contentInjector,
                                     Executor executor) {
        this(queryTransformer, queryRouter, contentAggregator, contentInjector, executor, false);
    }

    public DefaultRetrievalAugmentor(QueryTransformer queryTransformer,
                                     QueryRouter queryRouter,
                                     ContentAggregator contentAggregator,
                                     ContentInjector contentInjector,
                                     Executor executor,
                                     boolean offloadBlocking) {
        this.queryTransformer = getOrDefault(queryTransformer, DefaultQueryTransformer::new);
        this.queryRouter = ensureNotNull(queryRouter, "queryRouter");
        this.contentAggregator = getOrDefault(contentAggregator, DefaultContentAggregator::new);
        this.contentInjector = getOrDefault(contentInjector, DefaultContentInjector::new);
        this.executor = getOrDefault(executor, DefaultRetrievalAugmentor::createDefaultExecutor);
        this.asyncOffloadExecutor = getOrDefault(executor, DefaultExecutorProvider::getDefaultExecutorService);
        this.offloadBlocking = offloadBlocking;
    }

    private static ExecutorService createDefaultExecutor() {
        return new ThreadPoolExecutor(
            0, Integer.MAX_VALUE,
            1, SECONDS,
            new SynchronousQueue<>()
        );
    }

    @Override
    public AugmentationResult augment(AugmentationRequest augmentationRequest) {

        ChatMessage chatMessage = augmentationRequest.chatMessage();
        String queryText;
        if (chatMessage instanceof UserMessage userMessage) {
            queryText = userMessage.singleText();
        } else {
            throw new IllegalArgumentException("Unsupported message type: " + chatMessage.type());
        }
        Query originalQuery = Query.from(queryText, augmentationRequest.metadata());

        Collection<Query> queries = queryTransformer.transform(originalQuery);

        Map<Query, Collection<List<Content>>> queryToContents = process(queries);

        List<Content> contents = contentAggregator.aggregate(queryToContents);

        ChatMessage augmentedChatMessage = contentInjector.inject(contents, chatMessage);

        return AugmentationResult.builder()
            .chatMessage(augmentedChatMessage)
            .contents(contents)
            .build();
    }

    @Override
    public CompletableFuture<AugmentationResult> augmentAsync(AugmentationRequest augmentationRequest) {
        CompletableFuture<AugmentationResult> result = new CompletableFuture<>();
        CancellationChain chain = new CancellationChain(result);
        try {
            ChatMessage chatMessage = augmentationRequest.chatMessage();
            if (!(chatMessage instanceof UserMessage userMessage)) {
                throw new IllegalArgumentException("Unsupported message type: " + chatMessage.type());
            }
            Query originalQuery = Query.from(userMessage.singleText(), augmentationRequest.metadata());

            // Each stage runs on its component's native async path when available (query transformation, routing,
            // retrieval and aggregation can call an LLM, an embedding model, a vector store, ...); a stage that is not
            // async either offloads to the executor (offloadBlocking) or fails loudly - see nativeOrOffload. The stages
            // are composed, not joined, so the caller thread is never blocked; cancelling the returned future cancels
            // every in-flight stage via the CancellationChain (best-effort - see augmentAsync's javadoc).
            chain.track(nativeOrOffload(
                            () -> queryTransformer.transformAsync(originalQuery),
                            () -> queryTransformer.transform(originalQuery)))
                .thenCompose(queries -> processAsync(chain, queries))
                .thenCompose(queryToContents -> chain.track(nativeOrOffload(
                        () -> contentAggregator.aggregateAsync(queryToContents),
                        () -> contentAggregator.aggregate(queryToContents))))
                .thenApply(contents -> AugmentationResult.builder()
                    .chatMessage(contentInjector.inject(contents, chatMessage))
                    .contents(contents)
                    .build())
                .whenComplete((augmentationResult, error) -> {
                    if (error != null) {
                        result.completeExceptionally(unwrapCompletionException(error));
                    } else {
                        result.complete(augmentationResult);
                    }
                });
        } catch (Throwable t) {
            result.completeExceptionally(t);
        }
        return result;
    }

    private CompletableFuture<Map<Query, Collection<List<Content>>>> processAsync(
        CancellationChain chain, Collection<Query> queries) {
        if (queries.isEmpty()) {
            return completedFuture(emptyMap());
        }
        // Preserve request order (LinkedHashMap) so aggregation sees queries in the same order as the sync path.
        Map<Query, CompletableFuture<Collection<List<Content>>>> queryToFutureContents = new LinkedHashMap<>();
        for (Query query : queries) {
            CompletableFuture<Collection<List<Content>>> futureContents = chain.track(
                            nativeOrOffload(() -> queryRouter.routeAsync(query), () -> queryRouter.route(query)))
                    .thenCompose(retrievers -> retrieveFromAllAsync(chain, retrievers, query));
            queryToFutureContents.put(query, futureContents);
        }
        return chain.track(allOf(queryToFutureContents.values().toArray(new CompletableFuture[0])))
            .thenApply(ignored -> {
                Map<Query, Collection<List<Content>>> queryToContents = new LinkedHashMap<>();
                // join() never blocks here: allOf has already completed all futures.
                queryToFutureContents.forEach((query, future) -> queryToContents.put(query, future.join()));
                return queryToContents;
            });
    }

    private CompletableFuture<Collection<List<Content>>> retrieveFromAllAsync(
        CancellationChain chain, Collection<ContentRetriever> retrievers, Query query) {
        List<CompletableFuture<List<Content>>> futureContents = retrievers.stream()
            .map(retriever -> chain.track(retrieveOneAsync(retriever, query)))
            .collect(Collectors.toList());

        return chain.track(allOf(futureContents.toArray(new CompletableFuture[0])))
            .thenApply(ignored -> futureContents.stream()
                .map(CompletableFuture::join)
                .collect(Collectors.toList()));
    }

    private CompletableFuture<List<Content>> retrieveOneAsync(ContentRetriever retriever, Query query) {
        return nativeOrOffload(() -> retriever.retrieveAsync(query), () -> retriever.retrieve(query));
    }

    /**
     * Runs a stage on its component's native async method. A stage that is not genuinely async (its {@code *Async}
     * method reports being unimplemented via an {@link UnsupportedOperationException}) is either offloaded - its
     * blocking counterpart runs on the async offload executor (the shared virtual-thread executor by default) - when
     * {@code offloadBlocking}, or fails with an actionable message. Any other error propagates unchanged. No
     * reflection: async availability is discovered by calling it.
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
                    return supplyAsync(blockingCall, asyncOffloadExecutor);
                }
                return CompletableFuture.failedFuture(new UnsupportedFeatureException(cause.getMessage()
                        + " The RAG pipeline is not fully asynchronous. Either use async-capable components, or build"
                        + " this DefaultRetrievalAugmentor with offloadBlocking(true) to offload the blocking stage to"
                        + " the configured executor."));
            }
            return CompletableFuture.failedFuture(error);
        });
        // Cancellation does not flow to the stages a future is derived from, so link the caller-facing derived stage
        // back to the raw in-flight call - otherwise cancelling the pipeline never aborts the leaf I/O.
        propagateCancellation(result, async);
        return result;
    }

    private Map<Query, Collection<List<Content>>> process(Collection<Query> queries) {
        if (queries.size() == 1) {
            Query query = queries.iterator().next();
            Collection<ContentRetriever> retrievers = queryRouter.route(query);
            if (retrievers.size() == 1) {
                ContentRetriever contentRetriever = retrievers.iterator().next();
                List<Content> contents = contentRetriever.retrieve(query);
                return singletonMap(query, singletonList(contents));
            } else if (retrievers.size() > 1) {
                Collection<List<Content>> contents = retrieveFromAll(retrievers, query).join();
                return singletonMap(query, contents);
            } else {
                return emptyMap();
            }
        } else if (queries.size() > 1) {
            Map<Query, CompletableFuture<Collection<List<Content>>>> queryToFutureContents = new ConcurrentHashMap<>();
            queries.forEach(query -> {
                CompletableFuture<Collection<List<Content>>> futureContents =
                        supplyAsync(() -> queryRouter.route(query), executor)
                                .thenCompose(retrievers -> retrieveFromAll(retrievers, query));
                queryToFutureContents.put(query, futureContents);
            });
            return join(queryToFutureContents);
        } else {
            return emptyMap();
        }
    }

    private CompletableFuture<Collection<List<Content>>> retrieveFromAll(Collection<ContentRetriever> retrievers,
                                                                         Query query) {
        List<CompletableFuture<List<Content>>> futureContents = retrievers.stream()
            .map(retriever -> supplyAsync(() -> retriever.retrieve(query), executor))
            .collect(Collectors.toList());

        return allOf(futureContents.toArray(new CompletableFuture[0]))
            .thenApply(ignored ->
                futureContents.stream()
                    .map(CompletableFuture::join)
                    .collect(Collectors.toList()));
    }

    private static Map<Query, Collection<List<Content>>> join(
        Map<Query, CompletableFuture<Collection<List<Content>>>> queryToFutureContents) {
        return allOf(queryToFutureContents.values().toArray(new CompletableFuture[0]))
            .thenApply(ignored ->
                queryToFutureContents.entrySet().stream()
                    .collect(toMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue().join()
                    ))
            ).join();
    }

    public static DefaultRetrievalAugmentorBuilder builder() {
        return new DefaultRetrievalAugmentorBuilder();
    }

    public static class DefaultRetrievalAugmentorBuilder {

        private QueryTransformer queryTransformer;
        private QueryRouter queryRouter;
        private ContentAggregator contentAggregator;
        private ContentInjector contentInjector;
        private Executor executor;
        private boolean offloadBlocking;

        DefaultRetrievalAugmentorBuilder() {
        }

        public DefaultRetrievalAugmentorBuilder contentRetriever(ContentRetriever contentRetriever) {
            this.queryRouter = new DefaultQueryRouter(ensureNotNull(contentRetriever, "contentRetriever"));
            return this;
        }

        public DefaultRetrievalAugmentorBuilder queryTransformer(QueryTransformer queryTransformer) {
            this.queryTransformer = queryTransformer;
            return this;
        }

        public DefaultRetrievalAugmentorBuilder queryRouter(QueryRouter queryRouter) {
            this.queryRouter = queryRouter;
            return this;
        }

        public DefaultRetrievalAugmentorBuilder contentAggregator(ContentAggregator contentAggregator) {
            this.contentAggregator = contentAggregator;
            return this;
        }

        public DefaultRetrievalAugmentorBuilder contentInjector(ContentInjector contentInjector) {
            this.contentInjector = contentInjector;
            return this;
        }

        public DefaultRetrievalAugmentorBuilder executor(Executor executor) {
            this.executor = executor;
            return this;
        }

        /**
         * Controls what {@link #augmentAsync(AugmentationRequest)} does when a pipeline stage (query transformation,
         * routing, retrieval or aggregation) is not genuinely asynchronous (its {@code *Async} method is not
         * implemented).
         * <p>
         * By default ({@code false}), {@code augmentAsync} fails with an actionable error naming the blocking stage,
         * so a not-truly-async pipeline is never silently made "async" by parking a thread. Set to {@code true} to
         * instead offload the blocking stage to the configured {@link #executor(Executor) executor}. Has no effect on
         * the synchronous {@link #augment(AugmentationRequest)}.
         */
        public DefaultRetrievalAugmentorBuilder offloadBlocking(boolean offloadBlocking) {
            this.offloadBlocking = offloadBlocking;
            return this;
        }

        public DefaultRetrievalAugmentor build() {
            return new DefaultRetrievalAugmentor(
                    this.queryTransformer,
                    this.queryRouter,
                    this.contentAggregator,
                    this.contentInjector,
                    this.executor,
                    this.offloadBlocking);
        }
    }
}
