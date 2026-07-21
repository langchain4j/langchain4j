package dev.langchain4j.rag;

import dev.langchain4j.exception.AsyncNotSupportedException;
import static java.util.Arrays.asList;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import dev.langchain4j.internal.VirtualThreadUtils;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.aggregator.DefaultContentAggregator;
import dev.langchain4j.rag.content.injector.DefaultContentInjector;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Metadata;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.rag.query.router.DefaultQueryRouter;
import dev.langchain4j.rag.query.transformer.DefaultQueryTransformer;
import dev.langchain4j.rag.query.transformer.QueryTransformer;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class DefaultRetrievalAugmentorAsyncTest {

    private static AugmentationRequest request() {
        UserMessage userMessage = UserMessage.from("query");
        SystemMessage systemMessage = SystemMessage.from("Be polite");
        return new AugmentationRequest(userMessage, Metadata.from(userMessage, systemMessage, null, null));
    }

    @Test
    void augmentAsync_should_produce_the_same_result_as_augment__single_query_single_retriever() throws Exception {
        RetrievalAugmentor augmentor = DefaultRetrievalAugmentor.builder()
                .queryRouter(new DefaultQueryRouter(new FixedRetriever(Content.from("c1"), Content.from("c2"))))
                .executor(Runnable::run)
                .offloadBlocking(true)
                .build();

        AugmentationResult sync = augmentor.augment(request());
        AugmentationResult async = augmentor.augmentAsync(request()).get(5, SECONDS);

        assertThat(((UserMessage) async.chatMessage()).singleText())
                .isEqualTo(((UserMessage) sync.chatMessage()).singleText());
        assertThat(async.contents()).isEqualTo(sync.contents());
    }

    @Test
    void augmentAsync_should_produce_the_same_result_as_augment__multiple_queries_multiple_retrievers()
            throws Exception {
        QueryTransformer transformer = new FixedQueryTransformer(Query.from("q1"), Query.from("q2"));
        ContentRetriever retriever1 = new FixedRetriever(Content.from("c1"), Content.from("c2"));
        ContentRetriever retriever2 = new FixedRetriever(Content.from("c3"), Content.from("c4"));

        RetrievalAugmentor augmentor = DefaultRetrievalAugmentor.builder()
                .queryTransformer(transformer)
                .queryRouter(new DefaultQueryRouter(retriever1, retriever2))
                .contentAggregator(new DefaultContentAggregator())
                .contentInjector(new DefaultContentInjector())
                .executor(Executors.newFixedThreadPool(3))
                .offloadBlocking(true)
                .build();

        AugmentationResult sync = augmentor.augment(request());
        AugmentationResult async = augmentor.augmentAsync(request()).get(5, SECONDS);

        assertThat(((UserMessage) async.chatMessage()).singleText())
                .isEqualTo(((UserMessage) sync.chatMessage()).singleText());
        assertThat(async.contents()).containsExactlyElementsOf(sync.contents());
    }

    @Test
    void augmentAsync_default_should_fail_loudly_for_an_augmentor_that_has_not_opted_in() {
        // A custom RetrievalAugmentor that implements only the blocking augment(): the async default is fail-loud
        // (it is the AI Service, not the augmentor, that decides to offload a blocking augmentor - see
        // DefaultAiServices), so it never pretends to be asynchronous. The signal is delivered through the returned
        // future, not thrown synchronously.
        RetrievalAugmentor customAugmentor = augmentationRequest -> AugmentationResult.builder()
                .chatMessage(augmentationRequest.chatMessage())
                .contents(List.of())
                .build();

        assertThat(customAugmentor.augmentAsync(request())).isCompletedExceptionally();
        assertThatThrownBy(() -> customAugmentor.augmentAsync(request()).get())
                .isInstanceOf(ExecutionException.class)
                .cause()
                .isExactlyInstanceOf(AsyncNotSupportedException.class);
    }

    @Test
    void augmentAsync_should_not_block_the_caller_and_should_propagate_cancellation() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch retrieverStarted = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        BlockingRetriever retriever = new BlockingRetriever(retrieverStarted, release, Content.from("c1"));
        DefaultContentAggregator aggregator = new CountingAggregator();

        RetrievalAugmentor augmentor = DefaultRetrievalAugmentor.builder()
                .queryRouter(new DefaultQueryRouter(retriever))
                .contentAggregator(aggregator)
                .executor(executor)
                .offloadBlocking(true)
                .build();

        CompletableFuture<AugmentationResult> future = augmentor.augmentAsync(request());

        // The caller thread is not blocked: the future is returned before retrieval completes.
        assertThat(future.isDone()).isFalse();

        // Wait until retrieval is actually in flight, then cancel.
        assertThat(retrieverStarted.await(5, SECONDS)).isTrue();
        future.cancel(true);

        assertThat(future.isCancelled()).isTrue();

        // Let the (now-abandoned) retriever finish; aggregation must never run because the pipeline was cancelled.
        // (This is the OFFLOAD case: a blocking retrieve() offloaded to the executor cannot be interrupted, so it
        // runs to completion and its result is discarded - see the native-async case below for leaf cancellation.)
        release.countDown();
        Thread.sleep(200);
        assertThat(((CountingAggregator) aggregator).invocations).isZero();

        executor.shutdownNow();
    }

    @Test
    void augmentAsync_cancellation_aborts_the_in_flight_native_retriever_call() {
        // a genuinely-async retriever whose call never completes, exposed so we can assert it gets cancelled
        CompletableFuture<List<Content>> inFlight = new CompletableFuture<>();
        ContentRetriever nativeAsyncRetriever = new ContentRetriever() {
            @Override
            public List<Content> retrieve(Query query) {
                throw new UnsupportedOperationException("blocking path not used in this test");
            }

            @Override
            public CompletableFuture<List<Content>> retrieveAsync(Query query) {
                return inFlight;
            }
        };

        // offloadBlocking stays false: the retriever is genuinely async, so cancellation must reach its raw call
        RetrievalAugmentor augmentor = DefaultRetrievalAugmentor.builder()
                .queryRouter(new DefaultQueryRouter(nativeAsyncRetriever))
                .build();

        CompletableFuture<AugmentationResult> future = augmentor.augmentAsync(request());

        assertThat(future.isDone()).isFalse();
        assertThat(inFlight.isDone()).isFalse();

        future.cancel(true);

        // cancelling the pipeline aborts the in-flight leaf call (R2)
        assertThat(inFlight).isCancelled();
    }

    @Test
    void augmentAsync_does_not_mistake_an_incidental_UnsupportedOperationException_for_not_async() {
        // R4: a genuinely-async retriever whose retrieveAsync fails with a *plain* UnsupportedOperationException (e.g. a
        // bug like List.of(...).add(x)) must NOT be treated as the "not async" signal - it must propagate, not be
        // silently offloaded (which would mask the bug and double the cost), even with offloadBlocking(true).
        ContentRetriever asyncRetrieverWithBug = new ContentRetriever() {
            @Override
            public List<Content> retrieve(Query query) {
                return List.of(Content.from("blocking result that must NOT be used"));
            }

            @Override
            public CompletableFuture<List<Content>> retrieveAsync(Query query) {
                return CompletableFuture.failedFuture(new UnsupportedOperationException("incidental bug"));
            }
        };

        RetrievalAugmentor augmentor = DefaultRetrievalAugmentor.builder()
                .queryRouter(new DefaultQueryRouter(asyncRetrieverWithBug))
                .offloadBlocking(true) // even with offload ON, an incidental UOE must surface, not be swallowed
                .build();

        assertThatThrownBy(() -> augmentor.augmentAsync(request()).get(5, SECONDS))
                .isInstanceOf(ExecutionException.class)
                .cause()
                .isExactlyInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("incidental bug");
    }

    @Test
    void async_offload_runs_on_the_shared_virtual_thread_executor_by_default() throws Exception {
        // R5: a blocking stage offloaded on augmentAsync must run on the shared virtual-thread executor (non-pinning),
        // not the augmentor's platform-thread pool used for synchronous parallel retrieval.
        CompletableFuture<Boolean> offloadWasVirtual = new CompletableFuture<>();
        ContentRetriever blockingRetriever = query -> {
            offloadWasVirtual.complete(VirtualThreadUtils.isVirtualThread());
            return List.of(Content.from("c1"));
        };

        RetrievalAugmentor augmentor = DefaultRetrievalAugmentor.builder()
                .queryRouter(new DefaultQueryRouter(blockingRetriever))
                .offloadBlocking(true) // no custom executor -> async offload uses the shared virtual-thread executor
                .build();

        augmentor.augmentAsync(request()).get(5, SECONDS);

        // Only meaningful on a JVM that actually has virtual threads (21+); the shared executor falls back to
        // platform threads on older JVMs.
        assumeTrue(VirtualThreadUtils.isVirtualThreadsSupported());
        assertThat(offloadWasVirtual.get(5, SECONDS)).isTrue();
    }

    @Test
    void async_offload_honors_a_configured_executor() throws Exception {
        ExecutorService custom = Executors.newSingleThreadExecutor(r -> new Thread(r, "custom-offload-thread"));
        try {
            CompletableFuture<String> offloadThreadName = new CompletableFuture<>();
            ContentRetriever blockingRetriever = query -> {
                offloadThreadName.complete(Thread.currentThread().getName());
                return List.of(Content.from("c1"));
            };

            RetrievalAugmentor augmentor = DefaultRetrievalAugmentor.builder()
                    .queryRouter(new DefaultQueryRouter(blockingRetriever))
                    .executor(custom) // a configured executor is used for the async offload too
                    .offloadBlocking(true)
                    .build();

            augmentor.augmentAsync(request()).get(5, SECONDS);

            assertThat(offloadThreadName.get(5, SECONDS)).isEqualTo("custom-offload-thread");
        } finally {
            custom.shutdownNow();
        }
    }

    @Test
    void augmentAsync_should_match_augment_when_router_returns_no_retrievers() throws Exception {
        // Edge case: single query routed to zero retrievers. The sync path short-circuits to an empty map while
        // the async path uniformly fans out (producing an empty content collection); assert both yield the same
        // final result (the original, un-augmented message).
        RetrievalAugmentor augmentor = DefaultRetrievalAugmentor.builder()
                .queryRouter(query -> List.of())
                .executor(Runnable::run)
                .offloadBlocking(true)
                .build();

        AugmentationResult sync = augmentor.augment(request());
        AugmentationResult async = augmentor.augmentAsync(request()).get(5, SECONDS);

        assertThat(((UserMessage) async.chatMessage()).singleText())
                .isEqualTo(((UserMessage) sync.chatMessage()).singleText());
        assertThat(async.contents()).isEqualTo(sync.contents());
        assertThat(((UserMessage) async.chatMessage()).singleText()).isEqualTo("query");
    }

    @Test
    void augmentAsync_should_fail_the_future_when_a_retriever_throws() {
        RetrievalAugmentor augmentor = DefaultRetrievalAugmentor.builder()
                .queryRouter(new DefaultQueryRouter((ContentRetriever) query -> {
                    throw new RuntimeException("boom");
                }))
                .executor(Runnable::run)
                .offloadBlocking(true)
                .build();

        CompletableFuture<AugmentationResult> future = augmentor.augmentAsync(request());

        assertThatThrownBy(() -> future.get(5, SECONDS))
                .isInstanceOf(ExecutionException.class)
                .hasRootCauseInstanceOf(RuntimeException.class)
                .hasRootCauseMessage("boom");
    }

    @Test
    void augmentAsync_should_fail_the_future_for_an_unsupported_message_type() {
        RetrievalAugmentor augmentor = DefaultRetrievalAugmentor.builder()
                .queryRouter(new DefaultQueryRouter(new FixedRetriever(Content.from("c1"))))
                .executor(Runnable::run)
                .offloadBlocking(true)
                .build();

        AiMessage aiMessage = AiMessage.from("not a user message");
        Metadata metadata = Metadata.from(UserMessage.from("query"), SystemMessage.from("Be polite"), null, null);
        AugmentationRequest badRequest = new AugmentationRequest(aiMessage, metadata);

        assertThatThrownBy(() -> augmentor.augmentAsync(badRequest).get(5, SECONDS))
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void augmentAsync_should_prefer_a_retrievers_native_retrieveAsync_over_offloading_retrieve() throws Exception {
        AtomicBoolean asyncCalled = new AtomicBoolean(false);
        AtomicBoolean syncCalled = new AtomicBoolean(false);

        ContentRetriever nativeAsyncRetriever = new ContentRetriever() {
            @Override
            public List<Content> retrieve(Query query) {
                syncCalled.set(true);
                return List.of(Content.from("blocking"));
            }

            @Override
            public CompletableFuture<List<Content>> retrieveAsync(Query query) {
                asyncCalled.set(true);
                return CompletableFuture.completedFuture(List.of(Content.from("non-blocking")));
            }
        };

        RetrievalAugmentor augmentor = DefaultRetrievalAugmentor.builder()
                .queryRouter(new DefaultQueryRouter(nativeAsyncRetriever))
                .executor(Runnable::run)
                .offloadBlocking(true)
                .build();

        AugmentationResult result = augmentor.augmentAsync(request()).get(5, SECONDS);

        assertThat(asyncCalled).isTrue();
        assertThat(syncCalled).isFalse();
        assertThat(result.contents()).extracting(c -> c.textSegment().text()).containsExactly("non-blocking");
    }

    @Test
    void augmentAsync_should_fail_loudly_when_a_stage_is_blocking_and_offload_not_opted_in() {
        // A blocking retriever (no retrieveAsync) with offloadBlocking off: augmentAsync fails at runtime naming the
        // blocking stage and how to fix it, rather than silently offloading.
        RetrievalAugmentor augmentor = DefaultRetrievalAugmentor.builder()
                .queryRouter(new DefaultQueryRouter(new FixedRetriever(Content.from("c1"))))
                .executor(Runnable::run)
                .build();

        assertThatThrownBy(() -> augmentor.augmentAsync(request()).get(5, SECONDS))
                .isInstanceOf(ExecutionException.class)
                .cause()
                .isExactlyInstanceOf(UnsupportedFeatureException.class)
                .hasMessageContaining("retrieveAsync")
                .hasMessageContaining("offloadBlocking(true)");
    }

    // --- test doubles -------------------------------------------------------------------------------------------

    private static class FixedQueryTransformer implements QueryTransformer {
        private final List<Query> queries;

        FixedQueryTransformer(Query... queries) {
            this.queries = asList(queries);
        }

        @Override
        public Collection<Query> transform(Query query) {
            return queries;
        }
    }

    private static class FixedRetriever implements ContentRetriever {
        private final List<Content> contents;

        FixedRetriever(Content... contents) {
            this.contents = asList(contents);
        }

        @Override
        public List<Content> retrieve(Query query) {
            return contents;
        }
    }

    private static class BlockingRetriever implements ContentRetriever {
        private final CountDownLatch started;
        private final CountDownLatch release;
        private final List<Content> contents;

        BlockingRetriever(CountDownLatch started, CountDownLatch release, Content... contents) {
            this.started = started;
            this.release = release;
            this.contents = asList(contents);
        }

        @Override
        public List<Content> retrieve(Query query) {
            started.countDown();
            try {
                release.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return contents;
        }
    }

    private static class CountingAggregator extends DefaultContentAggregator {
        volatile int invocations = 0;

        @Override
        public List<Content> aggregate(java.util.Map<Query, Collection<List<Content>>> queryToContents) {
            invocations++;
            return super.aggregate(queryToContents);
        }
    }
}
