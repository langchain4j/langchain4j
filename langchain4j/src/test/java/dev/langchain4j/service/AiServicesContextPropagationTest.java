package dev.langchain4j.service;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.mock.ChatModelMock;
import dev.langchain4j.model.chat.mock.StreamingChatModelMock;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.embedding.mock.EmbeddingModelMock;
import dev.langchain4j.model.embedding.request.EmbeddingRequest;
import dev.langchain4j.model.embedding.response.EmbeddingResponse;
import dev.langchain4j.rag.DefaultRetrievalAugmentor;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.rag.query.transformer.QueryTransformer;
import dev.langchain4j.spi.ExecutorProvider;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Flow;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.Isolated;

/**
 * In a non-blocking AI Service call, the work LangChain4j offloads after the model has answered (tools) is submitted
 * from the thread that delivered the answer, not from the caller's thread. The context captured with
 * {@link ExecutorProvider#captureContext()} when the invocation starts is restored in that work.
 * <p>
 * {@link ExecutorProvider#set(ExecutorProvider)} is process-wide, so this class runs alone and sequentially.
 */
@Isolated
@Execution(ExecutionMode.SAME_THREAD)
class AiServicesContextPropagationTest {

    private static final ThreadLocal<String> REQUEST_ID = new ThreadLocal<>();

    private static final String USER_MESSAGE = "What is the capital of Germany?";

    private final ExecutorService executor = Executors.newCachedThreadPool();

    @AfterEach
    void cleanUp() {
        ExecutorProvider.set(null);
        REQUEST_ID.remove();
        executor.shutdownNow();
    }

    interface Assistant {

        String chat(String userMessage);

        CompletableFuture<String> chatAsync(String userMessage);

        Flow.Publisher<AiServiceStreamingEvent> chatEvents(String userMessage);

        TokenStream chatTokenStream(String userMessage);
    }

    static class Tools {

        final List<String> requestIdsSeen = new CopyOnWriteArrayList<>();

        @Tool
        String capitalOfGermany() {
            requestIdsSeen.add(String.valueOf(REQUEST_ID.get()));
            return "Berlin";
        }
    }

    @Test
    void should_run_tools_with_the_context_captured_when_the_invocation_started() throws Exception {

        // given
        ExecutorProvider.set(new CapturingExecutorProvider(executor));
        Tools tools = new Tools();
        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(ChatModelMock.thatAlwaysResponds(toolCall("1"), toolCall("2"), AiMessage.from("Berlin")))
                .tools(tools)
                .build();

        // when: two tool rounds, each submitted from the thread that delivered the model's answer
        String answer = withRequestId("request-42", () -> assistant.chatAsync(USER_MESSAGE)).get(10, SECONDS);

        // then
        assertThat(answer).isEqualTo("Berlin");
        assertThat(tools.requestIdsSeen).containsExactly("request-42", "request-42");
    }

    @Test
    void should_capture_context_with_a_provider_that_keeps_the_built_in_executor() throws Exception {

        // given: a provider that only captures context, and returns null to keep the built-in executor
        ExecutorProvider.set(new CapturingExecutorProvider(null));
        Tools tools = new Tools();
        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(ChatModelMock.thatAlwaysResponds(toolCall("1"), AiMessage.from("Berlin")))
                .tools(tools)
                .build();

        // when
        String answer = withRequestId("request-42", () -> assistant.chatAsync(USER_MESSAGE)).get(10, SECONDS);

        // then
        assertThat(answer).isEqualTo("Berlin");
        assertThat(tools.requestIdsSeen).containsExactly("request-42");
    }

    @Test
    void should_lose_the_context_with_an_executor_that_captures_it_on_submit() throws Exception {

        // given: an executor that propagates the context of the submitting thread, as a TaskDecorator does
        ExecutorProvider.set(() -> task -> {
            String submitterRequestId = REQUEST_ID.get();
            executor.execute(() -> runWithRequestId(submitterRequestId, task));
        });
        Tools tools = new Tools();
        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(ChatModelMock.thatAlwaysResponds(toolCall("1"), AiMessage.from("Berlin")))
                .tools(tools)
                .build();

        // when
        String answer = withRequestId("request-42", () -> assistant.chatAsync(USER_MESSAGE)).get(10, SECONDS);

        // then: the tool was submitted from the thread that delivered the model's answer, which has no context
        assertThat(answer).isEqualTo("Berlin");
        assertThat(tools.requestIdsSeen).containsExactly("null");
    }

    @Test
    void should_run_tools_of_streamed_events_with_the_captured_context() throws Exception {

        // given
        ExecutorProvider.set(new CapturingExecutorProvider(executor));
        Tools tools = new Tools();
        Assistant assistant = AiServices.builder(Assistant.class)
                .streamingChatModel(StreamingChatModelMock.thatAlwaysStreams(toolCall("1"), AiMessage.from("Berlin")))
                .tools(tools)
                .build();
        Flow.Publisher<AiServiceStreamingEvent> events =
                withRequestId("request-42", () -> assistant.chatEvents(USER_MESSAGE));

        // when: subscribed from another thread, so the stream (and the tool submission) runs without the context
        CompletableFuture<Void> completed = new CompletableFuture<>();
        CompletableFuture.runAsync(() -> events.subscribe(new Flow.Subscriber<>() {

            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(AiServiceStreamingEvent event) {}

            @Override
            public void onError(Throwable throwable) {
                completed.completeExceptionally(throwable);
            }

            @Override
            public void onComplete() {
                completed.complete(null);
            }
        }));
        completed.get(10, SECONDS);

        // then
        assertThat(tools.requestIdsSeen).containsExactly("request-42");
    }

    @Test
    void should_run_tools_of_a_token_stream_with_the_captured_context() throws Exception {

        // given: StreamingChatModelMock streams on a thread of its own
        ExecutorProvider.set(new CapturingExecutorProvider(executor));
        Tools tools = new Tools();
        Assistant assistant = AiServices.builder(Assistant.class)
                .streamingChatModel(StreamingChatModelMock.thatAlwaysStreams(toolCall("1"), AiMessage.from("Berlin")))
                .tools(tools)
                .executeToolsConcurrently(executor)
                .build();

        // when
        CompletableFuture<ChatResponse> response = new CompletableFuture<>();
        withRequestId("request-42", () -> assistant.chatTokenStream(USER_MESSAGE))
                .onPartialResponse(ignored -> {})
                .onCompleteResponse(response::complete)
                .onError(response::completeExceptionally)
                .start();

        // then
        assertThat(response.get(10, SECONDS).aiMessage().text()).isEqualTo("Berlin");
        assertThat(tools.requestIdsSeen).containsExactly("request-42");
    }

    @Test
    void should_run_concurrent_tools_of_a_blocking_call_with_the_captured_context() {

        // given
        ExecutorProvider.set(new CapturingExecutorProvider(executor));
        Tools tools = new Tools();
        // two tool calls in one response, so that they run concurrently
        AiMessage twoToolCalls = AiMessage.from(toolRequest("1"), toolRequest("2"));
        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(ChatModelMock.thatAlwaysResponds(twoToolCalls, AiMessage.from("Berlin")))
                .tools(tools)
                .executeToolsConcurrently(executor)
                .build();

        // when
        String answer = withRequestId("request-42", () -> assistant.chat(USER_MESSAGE));

        // then
        assertThat(answer).isEqualTo("Berlin");
        assertThat(tools.requestIdsSeen).containsExactly("request-42", "request-42");
    }

    @Test
    void should_run_offloaded_retrieval_with_the_captured_context() throws Exception {

        // given: the query transformation completes on another thread, from which the blocking retrieval is offloaded
        ExecutorProvider.set(new CapturingExecutorProvider(executor));
        List<String> requestIdsSeen = new CopyOnWriteArrayList<>();
        QueryTransformer asyncQueryTransformer = new QueryTransformer() {

            @Override
            public Collection<Query> transform(Query query) {
                return List.of(query);
            }

            @Override
            public CompletableFuture<Collection<Query>> transformAsync(Query query) {
                return CompletableFuture.supplyAsync(() -> List.of(query));
            }
        };
        ContentRetriever blockingRetriever = query -> {
            requestIdsSeen.add(String.valueOf(REQUEST_ID.get()));
            return List.of(Content.from("Berlin is the capital of Germany"));
        };
        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(ChatModelMock.thatAlwaysResponds("Berlin"))
                .retrievalAugmentor(DefaultRetrievalAugmentor.builder()
                        .queryTransformer(asyncQueryTransformer)
                        .contentRetriever(blockingRetriever)
                        .offloadBlocking(true)
                        .build())
                .build();

        // when
        String answer = withRequestId("request-42", () -> assistant.chatAsync(USER_MESSAGE)).get(10, SECONDS);

        // then
        assertThat(answer).isEqualTo("Berlin");
        assertThat(requestIdsSeen).containsExactly("request-42");
    }

    @Test
    void should_run_offloaded_embedding_of_the_content_retriever_with_the_captured_context() throws Exception {

        // given: EmbeddingModelMock is blocking only, so the retriever offloads the embedding of the query
        ExecutorProvider.set(new CapturingExecutorProvider(executor));
        List<String> requestIdsSeen = new CopyOnWriteArrayList<>();
        EmbeddingModelMock embeddingModel = new EmbeddingModelMock() {

            @Override
            public EmbeddingResponse doEmbed(EmbeddingRequest request) {
                requestIdsSeen.add(String.valueOf(REQUEST_ID.get()));
                return super.doEmbed(request);
            }
        };
        InMemoryEmbeddingStore<TextSegment> embeddingStore = new InMemoryEmbeddingStore<>();
        TextSegment segment = TextSegment.from("Berlin is the capital of Germany");
        embeddingStore.add(embeddingModel.embed(segment).content(), segment);
        requestIdsSeen.clear();
        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(ChatModelMock.thatAlwaysResponds("Berlin"))
                .contentRetriever(EmbeddingStoreContentRetriever.builder()
                        .embeddingStore(embeddingStore)
                        .embeddingModel(embeddingModel)
                        .offloadBlocking(true)
                        .build())
                .build();

        // when
        String answer = withRequestId("request-42", () -> assistant.chatAsync(USER_MESSAGE)).get(10, SECONDS);

        // then
        assertThat(answer).isEqualTo("Berlin");
        assertThat(requestIdsSeen).containsExactly("request-42");
    }

    @Test
    void should_work_with_a_provider_that_does_not_capture_context() throws Exception {

        // given: a provider written before captureContext() existed
        ExecutorProvider.set(() -> Runnable::run);
        Tools tools = new Tools();
        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(ChatModelMock.thatAlwaysResponds(toolCall("1"), AiMessage.from("Berlin")))
                .tools(tools)
                .build();

        // when
        String answer = assistant.chatAsync(USER_MESSAGE).get(10, SECONDS);

        // then
        assertThat(answer).isEqualTo("Berlin");
        assertThat(tools.requestIdsSeen).hasSize(1);
    }

    private static AiMessage toolCall(String id) {
        return AiMessage.from(toolRequest(id));
    }

    private static ToolExecutionRequest toolRequest(String id) {
        return ToolExecutionRequest.builder()
                .id(id)
                .name("capitalOfGermany")
                .arguments("{}")
                .build();
    }

    private static <T> T withRequestId(String requestId, Supplier<T> call) {
        REQUEST_ID.set(requestId);
        try {
            return call.get();
        } finally {
            REQUEST_ID.remove();
        }
    }

    private static void runWithRequestId(String requestId, Runnable task) {
        String previous = REQUEST_ID.get();
        REQUEST_ID.set(requestId);
        try {
            task.run();
        } finally {
            REQUEST_ID.set(previous);
        }
    }

    /**
     * Captures {@link #REQUEST_ID}, as a context-propagation library would capture MDC or a tracing span.
     */
    private record CapturingExecutorProvider(ExecutorService executor) implements ExecutorProvider {

        @Override
        public UnaryOperator<Runnable> captureContext() {
            String captured = REQUEST_ID.get();
            return task -> () -> runWithRequestId(captured, task);
        }
    }
}
