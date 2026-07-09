package dev.langchain4j.rag;

import static java.util.Arrays.asList;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.data.message.AiMessage;
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
                .build();

        AugmentationResult sync = augmentor.augment(request());
        AugmentationResult async = augmentor.augmentAsync(request()).get(5, SECONDS);

        assertThat(((UserMessage) async.chatMessage()).singleText())
                .isEqualTo(((UserMessage) sync.chatMessage()).singleText());
        assertThat(async.contents()).containsExactlyElementsOf(sync.contents());
    }

    @Test
    void augmentAsync_default_should_offload_blocking_augment_for_a_custom_augmentor() throws Exception {
        // A custom RetrievalAugmentor that implements only the blocking augment(): the async path must still work
        // via the offloading default, and off the calling thread.
        Thread callerThread = Thread.currentThread();
        CompletableFuture<Thread> augmentThread = new CompletableFuture<>();
        RetrievalAugmentor customAugmentor = augmentationRequest -> {
            augmentThread.complete(Thread.currentThread());
            return AugmentationResult.builder()
                    .chatMessage(augmentationRequest.chatMessage())
                    .contents(List.of())
                    .build();
        };

        AugmentationResult result = customAugmentor.augmentAsync(request()).get(5, SECONDS);

        assertThat(((UserMessage) result.chatMessage()).singleText()).isEqualTo("query");
        assertThat(augmentThread.get(5, SECONDS)).isNotSameAs(callerThread);
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
                .build();

        CompletableFuture<AugmentationResult> future = augmentor.augmentAsync(request());

        // The caller thread is not blocked: the future is returned before retrieval completes.
        assertThat(future.isDone()).isFalse();

        // Wait until retrieval is actually in flight, then cancel.
        assertThat(retrieverStarted.await(5, SECONDS)).isTrue();
        future.cancel(true);

        assertThat(future.isCancelled()).isTrue();

        // Let the (now-abandoned) retriever finish; aggregation must never run because the pipeline was cancelled.
        release.countDown();
        Thread.sleep(200);
        assertThat(((CountingAggregator) aggregator).invocations).isZero();

        executor.shutdownNow();
    }

    @Test
    void augmentAsync_should_match_augment_when_router_returns_no_retrievers() throws Exception {
        // Edge case: single query routed to zero retrievers. The sync path short-circuits to an empty map while
        // the async path uniformly fans out (producing an empty content collection); assert both yield the same
        // final result (the original, un-augmented message).
        RetrievalAugmentor augmentor = DefaultRetrievalAugmentor.builder()
                .queryRouter(query -> List.of())
                .executor(Runnable::run)
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
                .build();

        AugmentationResult result = augmentor.augmentAsync(request()).get(5, SECONDS);

        assertThat(asyncCalled).isTrue();
        assertThat(syncCalled).isFalse();
        assertThat(result.contents()).extracting(c -> c.textSegment().text()).containsExactly("non-blocking");
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
