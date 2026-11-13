package dev.langchain4j.model;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.exception.AsyncNotSupportedException;
import dev.langchain4j.internal.AsyncNotSupported;
import dev.langchain4j.internal.CurrentInvocation;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.ChatRequestOptions;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.TestStreamingChatResponseHandler;
import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.test.mock.MockChatModel;
import dev.langchain4j.test.mock.MockStreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatModelStreamingEvent;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.chat.router.ChatModelRouter;
import dev.langchain4j.model.chat.router.ChatModelRoutingRequest;
import dev.langchain4j.model.chat.router.ChatModelRoutingResult;
import dev.langchain4j.model.chat.router.RoutingChatModel;
import dev.langchain4j.model.chat.router.RoutingStreamingChatModel;
import dev.langchain4j.model.decision.listener.DecisionModelErrorContext;
import dev.langchain4j.model.decision.listener.DecisionModelListener;
import dev.langchain4j.model.decision.listener.DecisionModelRequestContext;
import dev.langchain4j.model.decision.listener.DecisionModelResponseContext;
import dev.langchain4j.test.mock.MockDecisionModel;
import dev.langchain4j.model.decision.request.DecisionRequest;
import dev.langchain4j.model.decision.request.YesNoQuestion;
import dev.langchain4j.model.decision.response.YesNoAnswer;
import dev.langchain4j.model.embedding.listener.EmbeddingModelErrorContext;
import dev.langchain4j.model.embedding.listener.EmbeddingModelListener;
import dev.langchain4j.model.embedding.listener.EmbeddingModelRequestContext;
import dev.langchain4j.model.embedding.listener.EmbeddingModelResponseContext;
import dev.langchain4j.test.mock.MockEmbeddingModel;
import dev.langchain4j.model.embedding.request.EmbeddingRequest;
import dev.langchain4j.model.moderation.ModerationRequest;
import dev.langchain4j.model.moderation.listener.ModerationModelListener;
import dev.langchain4j.model.moderation.listener.ModerationModelRequestContext;
import dev.langchain4j.model.moderation.listener.ModerationModelResponseContext;
import dev.langchain4j.test.mock.MockModerationModel;
import dev.langchain4j.model.scoring.listener.ScoringModelErrorContext;
import dev.langchain4j.model.scoring.listener.ScoringModelListener;
import dev.langchain4j.model.scoring.listener.ScoringModelRequestContext;
import dev.langchain4j.model.scoring.listener.ScoringModelResponseContext;
import dev.langchain4j.test.mock.MockScoringModel;
import dev.langchain4j.model.scoring.request.ScoringRequest;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.listener.ContentRetrieverListener;
import dev.langchain4j.rag.content.retriever.listener.ContentRetrieverRequestContext;
import dev.langchain4j.rag.content.retriever.listener.ContentRetrieverResponseContext;
import dev.langchain4j.rag.query.Query;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Flow;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.ParameterizedTest;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import java.util.stream.Stream;
import dev.langchain4j.store.embedding.listener.EmbeddingStoreResponseContext;
import dev.langchain4j.store.embedding.listener.EmbeddingStoreRequestContext;
import dev.langchain4j.store.embedding.listener.EmbeddingStoreErrorContext;
import dev.langchain4j.rag.content.retriever.listener.ContentRetrieverErrorContext;
import dev.langchain4j.model.moderation.listener.ModerationModelErrorContext;
import static org.junit.jupiter.api.Named.named;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;

class ListenerInvocationIdTest {

    static final ChatRequest CHAT_REQUEST = ChatRequest.builder()
            .messages(UserMessage.from("hi"))
            .build();

    @Test
    void chat_listener_should_get_no_invocation_id_outside_of_an_invocation() {
        RecordingChatModelListener listener = new RecordingChatModelListener();
        ChatModel model = MockChatModel.thatResponds("hello").withListeners(listener);

        model.chat(CHAT_REQUEST);

        assertThat(listener.invocationIds).hasSize(2).containsOnlyNulls();
    }

    @Test
    void chat_listener_should_get_the_current_invocation_id() {
        RecordingChatModelListener listener = new RecordingChatModelListener();
        ChatModel model = MockChatModel.thatResponds("hello").withListeners(listener);
        InvocationContext invocation = invocationContext();

        CurrentInvocation.callWith(invocation, () -> model.chat(CHAT_REQUEST));

        assertThat(listener.invocationIds).containsExactly(invocation.invocationId(), invocation.invocationId());
    }

    @Test
    void chat_listener_should_get_the_invocation_id_passed_in_the_options_over_the_current_one() {
        RecordingChatModelListener listener = new RecordingChatModelListener();
        ChatModel model = MockChatModel.thatResponds("hello").withListeners(listener);
        UUID passed = UUID.randomUUID();
        ChatRequestOptions options =
                ChatRequestOptions.builder().invocationId(passed).build();

        CurrentInvocation.callWith(invocationContext(), () -> model.chat(CHAT_REQUEST, options));

        assertThat(listener.invocationIds).containsExactly(passed, passed);
    }

    @Test
    void chat_listener_should_get_the_invocation_id_on_error() {
        RecordingChatModelListener listener = new RecordingChatModelListener();
        ChatModel model = MockChatModel.thatFails(new RuntimeException("failed")).withListeners(listener);
        InvocationContext invocation = invocationContext();

        assertThatThrownBy(() -> CurrentInvocation.callWith(invocation, () -> model.chat(CHAT_REQUEST)))
                .isExactlyInstanceOf(RuntimeException.class)
                .hasMessage("failed");

        assertThat(listener.invocationIds).containsExactly(invocation.invocationId(), invocation.invocationId());
        assertThat(listener.events).containsExactly("request", "error");
    }

    @Test
    void chat_listener_should_get_the_invocation_id_when_the_async_response_arrives_on_another_thread() {
        RecordingChatModelListener listener = new RecordingChatModelListener();
        ChatModel model = MockChatModel.thatResponds("hello").withListeners(listener);
        InvocationContext invocation = invocationContext();

        CompletableFuture<?> future = CurrentInvocation.callWith(invocation, () -> model.chatAsync(CHAT_REQUEST));

        assertThat(future).succeedsWithin(5, SECONDS);
        assertThat(listener.invocationIds).containsExactly(invocation.invocationId(), invocation.invocationId());
    }

    @Test
    void streaming_chat_listener_should_get_the_invocation_id_passed_in_the_options() {
        RecordingChatModelListener listener = new RecordingChatModelListener();
        StreamingChatModel model =
                MockStreamingChatModel.thatStreams("hel", "lo").withListeners(listener);
        UUID passed = UUID.randomUUID();
        TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();

        model.chat(CHAT_REQUEST, ChatRequestOptions.builder().invocationId(passed).build(), handler);

        assertThat(handler.get()).isNotNull();
        assertThat(listener.invocationIds).containsExactly(passed, passed);
    }

    @Test
    void streaming_chat_listener_should_get_the_current_invocation_id() {
        RecordingChatModelListener listener = new RecordingChatModelListener();
        StreamingChatModel model =
                MockStreamingChatModel.thatStreams("hel", "lo").withListeners(listener);
        InvocationContext invocation = invocationContext();
        TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();

        CurrentInvocation.callWith(invocation, () -> {
            model.chat(CHAT_REQUEST, handler);
            return null;
        });

        assertThat(handler.get()).isNotNull();
        assertThat(listener.invocationIds).containsExactly(invocation.invocationId(), invocation.invocationId());
    }

    @Test
    void publisher_chat_listener_should_get_the_current_invocation_id() {
        RecordingChatModelListener listener = new RecordingChatModelListener();
        StreamingChatModel model =
                MockStreamingChatModel.thatStreams("hel", "lo").withListeners(listener);
        InvocationContext invocation = invocationContext();

        CurrentInvocation.callWith(invocation, () -> {
            subscribe(model.chat(CHAT_REQUEST));
            return null;
        });

        assertThat(listener.invocationIds).containsExactly(invocation.invocationId(), invocation.invocationId());
    }

    @Test
    void publisher_chat_listener_should_get_no_invocation_id_and_no_attributes_outside_of_an_invocation() {
        RecordingChatModelListener listener = new RecordingChatModelListener();
        StreamingChatModel model =
                MockStreamingChatModel.thatStreams("hel", "lo").withListeners(listener);

        subscribe(model.chat(CHAT_REQUEST));

        assertThat(listener.invocationIds).hasSize(2).containsOnlyNulls();
        assertThat(listener.attributes).hasSize(2).allSatisfy(attributes -> assertThat(attributes).isEmpty());
    }

    @Test
    void routed_model_listener_should_get_the_invocation_id_passed_in_the_options() {
        RecordingChatModelListener listener = new RecordingChatModelListener();
        ChatModel routed = MockChatModel.thatResponds("hello").thenRepeats().withListeners(listener);
        ChatModel router = RoutingChatModel.builder()
                .route("only", routed)
                .defaultRoute("only")
                .router(asyncRouteTo("only"))
                .build();
        UUID passed = UUID.randomUUID();
        ChatRequestOptions options =
                ChatRequestOptions.builder().invocationId(passed).build();

        router.chat(CHAT_REQUEST, options);
        assertThat(router.chatAsync(CHAT_REQUEST, options)).succeedsWithin(5, SECONDS);

        assertThat(listener.invocationIds).containsExactly(passed, passed, passed, passed);
    }

    @Test
    void routed_model_listener_should_get_the_current_invocation_id_when_called_asynchronously() {
        RecordingChatModelListener listener = new RecordingChatModelListener();
        ChatModel routed = MockChatModel.thatResponds("hello").withListeners(listener);
        ChatModel router = RoutingChatModel.builder()
                .route("only", routed)
                .defaultRoute("only")
                .router(asyncRouteTo("only"))
                .build();
        InvocationContext invocation = invocationContext();

        CompletableFuture<?> future = CurrentInvocation.callWith(invocation, () -> router.chatAsync(CHAT_REQUEST));

        assertThat(future).succeedsWithin(5, SECONDS);
        assertThat(listener.invocationIds).containsExactly(invocation.invocationId(), invocation.invocationId());
    }

    @Test
    void decision_listener_should_get_the_current_invocation_id() {
        List<UUID> invocationIds = new CopyOnWriteArrayList<>();
        MockDecisionModel model = MockDecisionModel.thatAnswers(Map.of("spam", YesNoAnswer.from(0.9)))
                .thenRepeats()
                .withListeners(new DecisionModelListener() {
                    @Override
                    public void onRequest(DecisionModelRequestContext context) {
                        invocationIds.add(context.invocationId());
                    }

                    @Override
                    public void onResponse(DecisionModelResponseContext context) {
                        invocationIds.add(context.invocationId());
                    }

                    @Override
                    public void onError(DecisionModelErrorContext context) {
                        invocationIds.add(context.invocationId());
                    }
                });
        InvocationContext invocation = invocationContext();
        DecisionRequest request = DecisionRequest.builder()
                .input("Buy now!")
                .question("spam", YesNoQuestion.from("Is this spam?"))
                .build();

        model.decide(request);
        CurrentInvocation.callWith(invocation, () -> model.decide(request));
        assertThat(CurrentInvocation.callWith(invocation, () -> model.decideAsync(request)))
                .succeedsWithin(5, SECONDS);

        assertThat(invocationIds)
                .containsExactly(
                        null,
                        null,
                        invocation.invocationId(),
                        invocation.invocationId(),
                        invocation.invocationId(),
                        invocation.invocationId());
    }

    @Test
    void embedding_listener_should_get_the_current_invocation_id() {
        List<UUID> invocationIds = new CopyOnWriteArrayList<>();
        MockEmbeddingModel model = MockEmbeddingModel.thatEmbeds(1f, 2f, 3f).thenRepeats().withListeners(new EmbeddingModelListener() {
            @Override
            public void onRequest(EmbeddingModelRequestContext context) {
                invocationIds.add(context.invocationId());
            }

            @Override
            public void onResponse(EmbeddingModelResponseContext context) {
                invocationIds.add(context.invocationId());
            }

            @Override
            public void onError(EmbeddingModelErrorContext context) {
                invocationIds.add(context.invocationId());
            }
        });
        InvocationContext invocation = invocationContext();

        model.embed("hello");
        CurrentInvocation.callWith(invocation, () -> model.embed("hello"));
        CurrentInvocation.callWith(invocation, () -> model.embedAll(List.of(TextSegment.from("hello"))));

        assertThat(invocationIds)
                .containsExactly(
                        null,
                        null,
                        invocation.invocationId(),
                        invocation.invocationId(),
                        invocation.invocationId(),
                        invocation.invocationId());
    }

    @Test
    void moderation_listener_should_get_the_current_invocation_id() {
        List<UUID> invocationIds = new CopyOnWriteArrayList<>();
        MockModerationModel model = MockModerationModel.thatDoesNotFlag().thenRepeats().withListeners(new ModerationModelListener() {
            @Override
            public void onRequest(ModerationModelRequestContext context) {
                invocationIds.add(context.invocationId());
            }

            @Override
            public void onResponse(ModerationModelResponseContext context) {
                invocationIds.add(context.invocationId());
            }
        });
        InvocationContext invocation = invocationContext();

        model.moderate("hello");
        CurrentInvocation.callWith(invocation, () -> model.moderate("hello"));

        assertThat(invocationIds).containsExactly(null, null, invocation.invocationId(), invocation.invocationId());
    }

    @Test
    void scoring_listener_should_get_the_current_invocation_id() {
        List<UUID> invocationIds = new CopyOnWriteArrayList<>();
        MockScoringModel model = MockScoringModel.thatScores(0.5).thenRepeats().withListeners(new ScoringModelListener() {
            @Override
            public void onRequest(ScoringModelRequestContext context) {
                invocationIds.add(context.invocationId());
            }

            @Override
            public void onResponse(ScoringModelResponseContext context) {
                invocationIds.add(context.invocationId());
            }

            @Override
            public void onError(ScoringModelErrorContext context) {
                invocationIds.add(context.invocationId());
            }
        });
        InvocationContext invocation = invocationContext();
        ScoringRequest request = ScoringRequest.builder()
                .documents(List.of("a"))
                .query("query")
                .build();

        model.score(request);
        CurrentInvocation.callWith(invocation, () -> model.score(request));

        assertThat(invocationIds).containsExactly(null, null, invocation.invocationId(), invocation.invocationId());
    }

    @Test
    void scoring_listener_should_get_the_invocation_id_when_the_async_response_arrives_on_another_thread() {
        List<UUID> invocationIds = new CopyOnWriteArrayList<>();
        MockScoringModel model = MockScoringModel.thatScores(0.5) // completes asynchronously on another thread
                .withListeners(new ScoringModelListener() {
                    @Override
                    public void onRequest(ScoringModelRequestContext context) {
                        invocationIds.add(context.invocationId());
                    }

                    @Override
                    public void onResponse(ScoringModelResponseContext context) {
                        invocationIds.add(context.invocationId());
                    }
                });
        InvocationContext invocation = invocationContext();
        ScoringRequest request = ScoringRequest.builder()
                .documents(List.of("a"))
                .query("query")
                .build();

        assertThat(CurrentInvocation.callWith(invocation, () -> model.scoreAsync(request)))
                .succeedsWithin(5, SECONDS);

        assertThat(invocationIds).containsExactly(invocation.invocationId(), invocation.invocationId());
    }

    @Test
    void content_retriever_listener_should_get_the_current_invocation_id() {
        List<UUID> invocationIds = new CopyOnWriteArrayList<>();
        ContentRetriever retriever = ((ContentRetriever) query -> List.of())
                .addListener(new ContentRetrieverListener() {
                    @Override
                    public void onRequest(ContentRetrieverRequestContext context) {
                        invocationIds.add(context.invocationId());
                    }

                    @Override
                    public void onResponse(ContentRetrieverResponseContext context) {
                        invocationIds.add(context.invocationId());
                    }
                });
        InvocationContext invocation = invocationContext();

        retriever.retrieve(Query.from("hello"));
        CurrentInvocation.callWith(invocation, () -> retriever.retrieve(Query.from("hello")));

        assertThat(invocationIds).containsExactly(null, null, invocation.invocationId(), invocation.invocationId());
    }

    @Test
    void publisher_chat_listener_should_get_the_invocation_id_captured_when_chat_is_called() {
        RecordingChatModelListener listener = new RecordingChatModelListener();
        StreamingChatModel model =
                MockStreamingChatModel.thatStreams("hel", "lo").withListeners(listener);
        InvocationContext invocation = invocationContext();

        Flow.Publisher<ChatModelStreamingEvent> publisher =
                CurrentInvocation.callWith(invocation, () -> model.chat(CHAT_REQUEST));
        assertThat(CompletableFuture.runAsync(() -> subscribe(publisher))).succeedsWithin(5, SECONDS);

        assertThat(listener.invocationIds).containsExactly(invocation.invocationId(), invocation.invocationId());
    }

    @Test
    void routed_publisher_listener_should_get_the_invocation_id_captured_when_chat_is_called() {
        RecordingChatModelListener listener = new RecordingChatModelListener();
        StreamingChatModel routed =
                MockStreamingChatModel.thatStreams("hel", "lo").withListeners(listener);
        StreamingChatModel router = RoutingStreamingChatModel.builder()
                .route("only", routed)
                .defaultRoute("only")
                .router(asyncRouteTo("only"))
                .build();
        InvocationContext invocation = invocationContext();

        Flow.Publisher<ChatModelStreamingEvent> publisher =
                CurrentInvocation.callWith(invocation, () -> router.chat(CHAT_REQUEST));
        assertThat(CompletableFuture.runAsync(() -> subscribe(publisher))).succeedsWithin(5, SECONDS);

        assertThat(listener.invocationIds).containsExactly(invocation.invocationId(), invocation.invocationId());
    }

    @Test
    void streaming_chat_listener_should_get_the_invocation_id_on_error() {
        RecordingChatModelListener listener = new RecordingChatModelListener();
        StreamingChatModel model =
                MockStreamingChatModel.thatFails(new IllegalStateException("failed")).withListeners(listener);
        InvocationContext invocation = invocationContext();
        CompletableFuture<Throwable> error = new CompletableFuture<>();

        CurrentInvocation.callWith(invocation, () -> {
            model.chat(CHAT_REQUEST, new StreamingChatResponseHandler() {
                @Override
                public void onCompleteResponse(ChatResponse completeResponse) {}

                @Override
                public void onError(Throwable throwable) {
                    error.complete(throwable);
                }
            });
            return null;
        });

        assertThat(error).succeedsWithin(5, SECONDS).isInstanceOf(IllegalStateException.class);
        assertThat(listener.events).containsExactly("request", "error");
        assertThat(listener.invocationIds).containsExactly(invocation.invocationId(), invocation.invocationId());
    }

    @Test
    void publisher_chat_listener_should_get_the_invocation_id_on_error_on_another_thread() {
        RecordingChatModelListener listener = new RecordingChatModelListener();
        StreamingChatModel model =
                MockStreamingChatModel.thatFails(new IllegalStateException("failed")).withListeners(listener);
        InvocationContext invocation = invocationContext();

        Flow.Publisher<ChatModelStreamingEvent> publisher =
                CurrentInvocation.callWith(invocation, () -> model.chat(CHAT_REQUEST));
        CompletableFuture<Void> done = CompletableFuture.supplyAsync(() -> subscribeAsync(publisher))
                .thenCompose(future -> future);

        assertThat(done)
                .failsWithin(5, SECONDS)
                .withThrowableThat()
                .havingCause()
                .isExactlyInstanceOf(IllegalStateException.class)
                .withMessage("failed");
        assertThat(listener.events).containsExactly("request", "error");
        assertThat(listener.invocationIds).containsExactly(invocation.invocationId(), invocation.invocationId());
    }

    @Test
    void decision_listener_should_get_the_invocation_id_on_error() {
        List<UUID> invocationIds = new CopyOnWriteArrayList<>();
        MockDecisionModel model = MockDecisionModel.thatFails(new RuntimeException("failed"))
                .withListeners(new DecisionModelListener() {
                    @Override
                    public void onError(DecisionModelErrorContext context) {
                        invocationIds.add(context.invocationId());
                    }
                });
        InvocationContext invocation = invocationContext();
        DecisionRequest request = DecisionRequest.builder()
                .input("Buy now!")
                .question("spam", YesNoQuestion.from("Is this spam?"))
                .build();

        assertThatThrownBy(() -> CurrentInvocation.callWith(invocation, () -> model.decide(request)))
                .isExactlyInstanceOf(RuntimeException.class)
                .hasMessage("failed");
        assertThat(invocationIds).containsExactly(invocation.invocationId());
    }

    @Test
    void embedding_listener_should_get_the_invocation_id_on_error() {
        List<UUID> invocationIds = new CopyOnWriteArrayList<>();
        MockEmbeddingModel model = withoutAsyncSupport(MockEmbeddingModel.thatEmbeds(1f, 2f, 3f)).withListeners(new EmbeddingModelListener() {
            @Override
            public void onError(EmbeddingModelErrorContext context) {
                invocationIds.add(context.invocationId());
            }
        });
        InvocationContext invocation = invocationContext();
        EmbeddingRequest request = EmbeddingRequest.builder()
                .textSegments(List.of(TextSegment.from("hello")))
                .build();

        assertThat(CurrentInvocation.callWith(invocation, () -> model.embedAsync(request)))
                .failsWithin(5, SECONDS)
                .withThrowableThat()
                .havingCause()
                .isExactlyInstanceOf(AsyncNotSupportedException.class);
        assertThat(invocationIds).containsExactly(invocation.invocationId());
    }

    @Test
    void scoring_listener_should_get_the_invocation_id_on_error() {
        List<UUID> invocationIds = new CopyOnWriteArrayList<>();
        MockScoringModel model = MockScoringModel.thatFails(new RuntimeException("failed"))
                .withListeners(new ScoringModelListener() {
                    @Override
                    public void onError(ScoringModelErrorContext context) {
                        invocationIds.add(context.invocationId());
                    }
                });
        InvocationContext invocation = invocationContext();
        ScoringRequest request = ScoringRequest.builder()
                .documents(List.of("a"))
                .query("query")
                .build();

        assertThatThrownBy(() -> CurrentInvocation.callWith(invocation, () -> model.score(request)))
                .isExactlyInstanceOf(RuntimeException.class)
                .hasMessage("failed");
        assertThat(invocationIds).containsExactly(invocation.invocationId());
    }

    @Test
    @SuppressWarnings("deprecation")
    void deprecated_constructors_should_still_work_without_invocation_id() {
        Map<Object, Object> attributes = new ConcurrentHashMap<>();
        DecisionRequest decisionRequest = DecisionRequest.builder()
                .input("Buy now!")
                .question("spam", YesNoQuestion.from("Is this spam?"))
                .build();
        ModerationRequest moderationRequest = ModerationRequest.builder().texts(List.of("hello")).build();

        assertThat(new ChatModelRequestContext(CHAT_REQUEST, ModelProvider.OTHER, attributes).invocationId()).isNull();
        assertThat(new ChatModelErrorContext(new RuntimeException(), CHAT_REQUEST, ModelProvider.OTHER, attributes)
                        .chatRequest())
                .isSameAs(CHAT_REQUEST);
        assertThat(new DecisionModelRequestContext(decisionRequest, ModelProvider.OTHER, attributes).invocationId())
                .isNull();
        assertThat(new ModerationModelRequestContext(moderationRequest, ModelProvider.OTHER, attributes)
                        .invocationId())
                .isNull();
    }

    @ParameterizedTest
    @MethodSource("builders_without_required_fields")
    void builders_should_reject_missing_required_fields(ThrowingCallable build, String expectedMessage) {
        assertThatThrownBy(build)
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage(expectedMessage);
    }

    static Stream<Arguments> builders_without_required_fields() {
        return Stream.of(
                Arguments.of(
                        named("ChatModelRequestContext", (ThrowingCallable) () -> ChatModelRequestContext.builder()
                                .build()),
                        "chatRequest cannot be null"),
                Arguments.of(
                        named("ChatModelResponseContext", (ThrowingCallable) () -> ChatModelResponseContext.builder()
                                .build()),
                        "chatResponse cannot be null"),
                Arguments.of(
                        named("ChatModelErrorContext", (ThrowingCallable) () -> ChatModelErrorContext.builder()
                                .build()),
                        "error cannot be null"),
                Arguments.of(
                        named(
                                "DecisionModelRequestContext",
                                (ThrowingCallable) () -> DecisionModelRequestContext.builder().build()),
                        "decisionRequest cannot be null"),
                Arguments.of(
                        named(
                                "DecisionModelResponseContext",
                                (ThrowingCallable) () -> DecisionModelResponseContext.builder().build()),
                        "decisionResponse cannot be null"),
                Arguments.of(
                        named(
                                "DecisionModelErrorContext",
                                (ThrowingCallable) () -> DecisionModelErrorContext.builder().build()),
                        "error cannot be null"),
                Arguments.of(
                        named(
                                "ModerationModelRequestContext",
                                (ThrowingCallable) () -> ModerationModelRequestContext.builder().build()),
                        "moderationRequest cannot be null"),
                Arguments.of(
                        named(
                                "ModerationModelResponseContext",
                                (ThrowingCallable) () -> ModerationModelResponseContext.builder().build()),
                        "moderationResponse cannot be null"),
                Arguments.of(
                        named(
                                "ModerationModelErrorContext",
                                (ThrowingCallable) () -> ModerationModelErrorContext.builder().build()),
                        "error cannot be null"),
                Arguments.of(
                        named(
                                "EmbeddingStoreRequestContext.Search",
                                (ThrowingCallable) () -> EmbeddingStoreRequestContext.Search.<String>builder()
                                        .build()),
                        "embeddingStore cannot be null"),
                Arguments.of(
                        named(
                                "EmbeddingStoreResponseContext.Search",
                                (ThrowingCallable) () -> EmbeddingStoreResponseContext.Search.<String>builder()
                                        .build()),
                        "requestContext cannot be null"),
                Arguments.of(
                        named(
                                "EmbeddingStoreErrorContext",
                                (ThrowingCallable) () -> EmbeddingStoreErrorContext.<String>builder().build()),
                        "error cannot be null"));
    }

    @Test
    void chat_listener_should_get_the_invocation_id_when_the_async_call_fails_on_another_thread() {
        RecordingChatModelListener listener = new RecordingChatModelListener();
        ChatModel model = MockChatModel.thatFails(new RuntimeException("failed")).withListeners(listener);
        InvocationContext invocation = invocationContext();

        CompletableFuture<?> future = CurrentInvocation.callWith(invocation, () -> model.chatAsync(CHAT_REQUEST));

        assertThat(future)
                .failsWithin(5, SECONDS)
                .withThrowableThat()
                .havingCause()
                .isExactlyInstanceOf(RuntimeException.class)
                .withMessage("failed");
        assertThat(listener.events).containsExactly("request", "error");
        assertThat(listener.invocationIds).containsExactly(invocation.invocationId(), invocation.invocationId());
    }

    @Test
    void decision_listener_should_get_the_invocation_id_when_the_async_call_fails_on_another_thread() {
        List<UUID> invocationIds = new CopyOnWriteArrayList<>();
        MockDecisionModel model = MockDecisionModel.thatFails(new IllegalStateException("failed")) // fails on another thread
                .withListeners(new DecisionModelListener() {
                    @Override
                    public void onRequest(DecisionModelRequestContext context) {
                        invocationIds.add(context.invocationId());
                    }

                    @Override
                    public void onError(DecisionModelErrorContext context) {
                        invocationIds.add(context.invocationId());
                    }
                });
        InvocationContext invocation = invocationContext();
        DecisionRequest request = DecisionRequest.builder()
                .input("Buy now!")
                .question("spam", YesNoQuestion.from("Is this spam?"))
                .build();

        assertThat(CurrentInvocation.callWith(invocation, () -> model.decideAsync(request)))
                .failsWithin(5, SECONDS)
                .withThrowableThat()
                .havingCause()
                .isExactlyInstanceOf(IllegalStateException.class)
                .withMessage("failed");
        assertThat(invocationIds).containsExactly(invocation.invocationId(), invocation.invocationId());
    }

    @Test
    void moderation_listener_should_get_the_invocation_id_on_error() {
        List<UUID> invocationIds = new CopyOnWriteArrayList<>();
        MockModerationModel model = MockModerationModel.thatModerates(request -> {
                    throw new IllegalStateException("failed");
                })
                .withListeners(new ModerationModelListener() {
                    @Override
                    public void onError(ModerationModelErrorContext context) {
                        invocationIds.add(context.invocationId());
                    }
                });
        InvocationContext invocation = invocationContext();

        assertThatThrownBy(() -> CurrentInvocation.callWith(invocation, () -> model.moderate("hello")))
                .isExactlyInstanceOf(IllegalStateException.class)
                .hasMessage("failed");
        assertThat(invocationIds).containsExactly(invocation.invocationId());
    }

    @Test
    void content_retriever_listener_should_get_the_invocation_id_on_error() {
        List<UUID> invocationIds = new CopyOnWriteArrayList<>();
        ContentRetriever retriever = ((ContentRetriever) query -> {
                    throw new IllegalStateException("failed");
                })
                .addListener(new ContentRetrieverListener() {
                    @Override
                    public void onError(ContentRetrieverErrorContext context) {
                        invocationIds.add(context.invocationId());
                    }
                });
        InvocationContext invocation = invocationContext();

        assertThatThrownBy(() -> CurrentInvocation.callWith(invocation, () -> retriever.retrieve(Query.from("hello"))))
                .isExactlyInstanceOf(IllegalStateException.class)
                .hasMessage("failed");
        assertThat(invocationIds).containsExactly(invocation.invocationId());
    }

    @Test
    void decision_listener_should_be_notified_without_the_current_invocation() {
        List<UUID> seenInListener = new CopyOnWriteArrayList<>();
        MockDecisionModel model = MockDecisionModel.thatAnswers(Map.of("spam", YesNoAnswer.from(0.9)))
                .withListeners(new DecisionModelListener() {
                    @Override
                    public void onRequest(DecisionModelRequestContext context) {
                        seenInListener.add(CurrentInvocation.invocationId());
                    }

                    @Override
                    public void onResponse(DecisionModelResponseContext context) {
                        seenInListener.add(CurrentInvocation.invocationId());
                    }
                });
        DecisionRequest request = DecisionRequest.builder()
                .input("Buy now!")
                .question("spam", YesNoQuestion.from("Is this spam?"))
                .build();

        CurrentInvocation.callWith(invocationContext(), () -> model.decide(request));

        assertThat(seenInListener).hasSize(2).containsOnlyNulls();
    }

    @Test
    void embedding_listener_should_be_notified_without_the_current_invocation() {
        List<UUID> seenInListener = new CopyOnWriteArrayList<>();
        MockEmbeddingModel model = MockEmbeddingModel.thatEmbeds(1f, 2f, 3f).withListeners(new EmbeddingModelListener() {
            @Override
            public void onRequest(EmbeddingModelRequestContext context) {
                seenInListener.add(CurrentInvocation.invocationId());
            }

            @Override
            public void onResponse(EmbeddingModelResponseContext context) {
                seenInListener.add(CurrentInvocation.invocationId());
            }
        });

        CurrentInvocation.callWith(invocationContext(), () -> model.embed("hello"));

        assertThat(seenInListener).hasSize(2).containsOnlyNulls();
    }

    @Test
    void moderation_listener_should_be_notified_without_the_current_invocation() {
        List<UUID> seenInListener = new CopyOnWriteArrayList<>();
        MockModerationModel model = MockModerationModel.thatDoesNotFlag().withListeners(new ModerationModelListener() {
            @Override
            public void onRequest(ModerationModelRequestContext context) {
                seenInListener.add(CurrentInvocation.invocationId());
            }

            @Override
            public void onResponse(ModerationModelResponseContext context) {
                seenInListener.add(CurrentInvocation.invocationId());
            }
        });

        CurrentInvocation.callWith(invocationContext(), () -> model.moderate("hello"));

        assertThat(seenInListener).hasSize(2).containsOnlyNulls();
    }

    @Test
    void scoring_listener_should_be_notified_without_the_current_invocation() {
        List<UUID> seenInListener = new CopyOnWriteArrayList<>();
        MockScoringModel model = MockScoringModel.thatScores(0.5).withListeners(new ScoringModelListener() {
            @Override
            public void onRequest(ScoringModelRequestContext context) {
                seenInListener.add(CurrentInvocation.invocationId());
            }

            @Override
            public void onResponse(ScoringModelResponseContext context) {
                seenInListener.add(CurrentInvocation.invocationId());
            }
        });
        ScoringRequest request = ScoringRequest.builder()
                .documents(List.of("a"))
                .query("query")
                .build();

        CurrentInvocation.callWith(invocationContext(), () -> model.score(request));

        assertThat(seenInListener).hasSize(2).containsOnlyNulls();
    }

    @Test
    void content_retriever_listener_should_be_notified_without_the_current_invocation() {
        List<UUID> seenInListener = new CopyOnWriteArrayList<>();
        ContentRetriever retriever = ((ContentRetriever) query -> List.of())
                .addListener(new ContentRetrieverListener() {
                    @Override
                    public void onRequest(ContentRetrieverRequestContext context) {
                        seenInListener.add(CurrentInvocation.invocationId());
                    }

                    @Override
                    public void onResponse(ContentRetrieverResponseContext context) {
                        seenInListener.add(CurrentInvocation.invocationId());
                    }
                });

        CurrentInvocation.callWith(invocationContext(), () -> retriever.retrieve(Query.from("hello")));

        assertThat(seenInListener).hasSize(2).containsOnlyNulls();
    }

    @Test
    void routed_publisher_created_outside_of_an_invocation_should_stay_without_invocation_id() {
        RecordingChatModelListener listener = new RecordingChatModelListener();
        StreamingChatModel routed =
                MockStreamingChatModel.thatStreams("hel", "lo").withListeners(listener);
        StreamingChatModel router = RoutingStreamingChatModel.builder()
                .route("only", routed)
                .defaultRoute("only")
                .router(asyncRouteTo("only"))
                .build();

        Flow.Publisher<ChatModelStreamingEvent> publisher = router.chat(CHAT_REQUEST);
        CurrentInvocation.callWith(invocationContext(), () -> {
            subscribe(publisher);
            return null;
        });

        assertThat(listener.invocationIds).hasSize(2).containsOnlyNulls();
    }

    @Test
    void router_should_see_the_invocation_id_passed_in_the_options() {
        List<UUID> seenByRouter = new CopyOnWriteArrayList<>();
        ChatModelRouter recordingRouter = new ChatModelRouter() {
            @Override
            public ChatModelRoutingResult route(ChatModelRoutingRequest request) {
                seenByRouter.add(CurrentInvocation.invocationId());
                return ChatModelRoutingResult.route("only");
            }

            @Override
            public CompletableFuture<ChatModelRoutingResult> routeAsync(ChatModelRoutingRequest request) {
                seenByRouter.add(CurrentInvocation.invocationId());
                return CompletableFuture.completedFuture(ChatModelRoutingResult.route("only"));
            }
        };
        ChatModel router = RoutingChatModel.builder()
                .route("only", MockChatModel.thatResponds("hello").thenRepeats())
                .defaultRoute("only")
                .router(recordingRouter)
                .build();
        StreamingChatModel streamingRouter = RoutingStreamingChatModel.builder()
                .route("only", MockStreamingChatModel.thatResponds("hello").thenRepeats())
                .defaultRoute("only")
                .router(recordingRouter)
                .build();
        UUID passed = UUID.randomUUID();
        ChatRequestOptions options =
                ChatRequestOptions.builder().invocationId(passed).build();

        router.chat(CHAT_REQUEST, options);
        assertThat(router.chatAsync(CHAT_REQUEST, options)).succeedsWithin(5, SECONDS);
        TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();
        streamingRouter.chat(CHAT_REQUEST, options, handler);
        handler.get();

        assertThat(seenByRouter).containsExactly(passed, passed, passed);
    }

    private static ChatModelRouter asyncRouteTo(String routeName) {
        return new ChatModelRouter() {
            @Override
            public ChatModelRoutingResult route(ChatModelRoutingRequest request) {
                return ChatModelRoutingResult.route(routeName);
            }

            @Override
            public CompletableFuture<ChatModelRoutingResult> routeAsync(
                    ChatModelRoutingRequest request) {
                return CompletableFuture.supplyAsync(() -> ChatModelRoutingResult.route(routeName));
            }
        };
    }

    private static void subscribe(Flow.Publisher<ChatModelStreamingEvent> publisher) {
        assertThat(subscribeAsync(publisher)).succeedsWithin(5, SECONDS);
    }

    private static CompletableFuture<Void> subscribeAsync(Flow.Publisher<ChatModelStreamingEvent> publisher) {
        CompletableFuture<Void> done = new CompletableFuture<>();
        publisher.subscribe(new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(ChatModelStreamingEvent item) {}

            @Override
            public void onError(Throwable throwable) {
                done.completeExceptionally(throwable);
            }

            @Override
            public void onComplete() {
                done.complete(null);
            }
        });
        return done;
    }

    private static InvocationContext invocationContext() {
        return InvocationContext.builder().invocationId(UUID.randomUUID()).build();
    }

    static class RecordingChatModelListener implements ChatModelListener {

        final List<UUID> invocationIds = new CopyOnWriteArrayList<>();
        final List<String> events = new CopyOnWriteArrayList<>();
        final List<Map<Object, Object>> attributes = new CopyOnWriteArrayList<>();

        @Override
        public void onRequest(ChatModelRequestContext context) {
            invocationIds.add(context.invocationId());
            events.add("request");
            attributes.add(context.attributes());
        }

        @Override
        public void onResponse(ChatModelResponseContext context) {
            invocationIds.add(context.invocationId());
            events.add("response");
            attributes.add(context.attributes());
        }

        @Override
        public void onError(ChatModelErrorContext context) {
            invocationIds.add(context.invocationId());
            events.add("error");
        }
    }

    private static MockEmbeddingModel withoutAsyncSupport(MockEmbeddingModel model) {
        MockEmbeddingModel withoutAsync = spy(model); // an embedding model without asynchronous support
        doAnswer(invocation -> AsyncNotSupported.failedFuture(MockEmbeddingModel.class, "doEmbedAsync"))
                .when(withoutAsync)
                .doEmbedAsync(any());
        return withoutAsync;
    }
}
