package dev.langchain4j.internal;

import static dev.langchain4j.internal.TestObservabilityListenersProviders.RECEIVED;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.observability.ProviderOutsideLangChain4j;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.model.chat.TestStreamingChatResponseHandler;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.test.mock.ChatModelMock;
import dev.langchain4j.test.mock.StreamingChatModelMock;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatModelStreamingEvent;
import dev.langchain4j.model.decision.listener.DecisionModelListener;
import dev.langchain4j.model.decision.listener.DecisionModelRequestContext;
import dev.langchain4j.test.mock.DecisionModelMock;
import dev.langchain4j.model.decision.request.DecisionRequest;
import dev.langchain4j.model.decision.request.YesNoQuestion;
import dev.langchain4j.model.embedding.EmbeddingModelListenerUtils;
import dev.langchain4j.model.embedding.listener.EmbeddingModelListener;
import dev.langchain4j.model.embedding.listener.EmbeddingModelRequestContext;
import dev.langchain4j.test.mock.EmbeddingModelMock;
import dev.langchain4j.model.embedding.request.EmbeddingRequest;
import dev.langchain4j.model.moderation.listener.ModerationModelListener;
import dev.langchain4j.model.moderation.listener.ModerationModelRequestContext;
import dev.langchain4j.test.mock.ModerationModelMock;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.scoring.listener.ScoringModelListener;
import dev.langchain4j.model.scoring.listener.ScoringModelRequestContext;
import dev.langchain4j.test.mock.ScoringModelMock;
import dev.langchain4j.model.scoring.request.ScoringRequest;
import dev.langchain4j.observability.api.AiServiceListenerRegistrar;
import dev.langchain4j.observability.api.DefaultAiServiceListenerRegistrar;
import dev.langchain4j.observability.api.event.AiServiceEvent;
import dev.langchain4j.observability.api.event.AiServiceStartedEvent;
import dev.langchain4j.observability.api.listener.AiServiceListener;
import dev.langchain4j.spi.observability.ObservabilityListenersProvider;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ObservabilityListenersTest {

    private static final ChatRequest CHAT_REQUEST =
            ChatRequest.builder().messages(UserMessage.from("Hello")).build();

    private static final DecisionRequest DECISION_REQUEST = DecisionRequest.builder()
            .input("Buy now!")
            .question("spam", YesNoQuestion.from("Is this spam?"))
            .build();

    @TempDir
    Path directory;

    @BeforeEach
    void clear() {
        RECEIVED.clear();
    }

    @Test
    void should_register_provided_ai_service_listeners_with_new_registrars() throws Exception {
        AiServiceStartedEvent event = startedEvent();

        withProviders(() -> AiServiceListenerRegistrar.newInstance().fireEvent(event), recording());

        assertThat(RECEIVED).containsExactly(event);
    }

    @Test
    void should_register_provided_ai_service_listeners_once_with_a_shared_registrar() throws Exception {
        AiServiceStartedEvent event = startedEvent();
        AiServiceListenerRegistrar shared = new DefaultAiServiceListenerRegistrar();
        List<AiServiceListener<?>> registered = new ArrayList<>();
        AiServiceListenerRegistrar recordingRegistrar = new AiServiceListenerRegistrar() {
            @Override
            public <T extends AiServiceEvent> void register(AiServiceListener<T> listener) {
                registered.add(listener);
                shared.register(listener);
            }

            @Override
            public <T extends AiServiceEvent> void unregister(AiServiceListener<T> listener) {
                shared.unregister(listener);
            }

            @Override
            public <T extends AiServiceEvent> void fireEvent(T event) {
                shared.fireEvent(event);
            }

            @Override
            public void shouldThrowExceptionOnEventError(boolean shouldThrowExceptionOnEventError) {}
        };

        withProviders(
                () -> {
                    ObservabilityListeners.registerWith(recordingRegistrar);
                    ObservabilityListeners.registerWith(recordingRegistrar);
                },
                recording());
        recordingRegistrar.fireEvent(event);

        assertThat(registered).hasSize(1);
        assertThat(RECEIVED).containsExactly(event);
    }

    @Test
    void should_not_fail_when_a_registrar_does_not_accept_the_provided_listeners() throws Exception {
        AiServiceStartedEvent event = startedEvent();
        AiServiceListenerRegistrar throwing = new DefaultAiServiceListenerRegistrar() {
            @Override
            public <T extends AiServiceEvent> void register(AiServiceListener<T> listener) {
                throw new UnsupportedOperationException("does not accept listeners");
            }
        };

        withProviders(() -> ObservabilityListeners.registerWith(throwing), recording());
        throwing.fireEvent(event);

        assertThat(RECEIVED).isEmpty();
    }

    @Test
    void should_skip_providers_that_cannot_be_loaded_and_use_the_others() throws Exception {
        AiServiceStartedEvent event = startedEvent();

        withProviders(
                () -> AiServiceListenerRegistrar.newInstance().fireEvent(event),
                TestObservabilityListenersProviders.WithoutNoArgConstructor.class.getName(),
                TestObservabilityListenersProviders.ThrowingFromConstructor.class.getName(),
                TestObservabilityListenersProviders.ThrowingFromStaticInitializer.class.getName(),
                "com.example.DoesNotExist",
                recording());

        assertThat(RECEIVED).containsExactly(event);
    }

    @Test
    void should_not_fail_when_a_provider_cannot_provide_its_listeners() throws Exception {
        withProviders(
                () -> {
                    AiServiceListenerRegistrar.newInstance().fireEvent(startedEvent());
                    assertThat(ChatModelMock.thatResponds("Hi")
                                    .chat(CHAT_REQUEST)
                                    .aiMessage()
                                    .text())
                            .isEqualTo("Hi");
                },
                TestObservabilityListenersProviders.ThrowingFromGetter.class.getName());
    }

    @Test
    void should_notify_provided_chat_model_listeners_in_addition_to_the_model_listeners() throws Exception {
        List<ChatModelRequestContext> ownRequests = new ArrayList<>();
        ChatModelMock model = ChatModelMock.thatResponds("Hi").withListeners(new ChatModelListener() {
            @Override
            public void onRequest(ChatModelRequestContext requestContext) {
                ownRequests.add(requestContext);
            }
        });

        withProviders(() -> model.chat("Hello"), recording());

        assertThat(ownRequests).hasSize(1);
        assertThat(RECEIVED).singleElement().isInstanceOf(ChatModelRequestContext.class);
        assertThat(((ChatModelRequestContext) RECEIVED.get(0)).attributes()).isSameAs(ownRequests.get(0).attributes());
    }

    @Test
    void should_notify_provided_scoring_model_listeners() throws Exception {
        ScoringModelMock model = ScoringModelMock.thatScores(document -> 0.5);

        withProviders(
                () -> model.score(ScoringRequest.builder()
                        .documents(List.of("a document"))
                        .query("a query")
                        .build()),
                recording());

        assertThat(RECEIVED).singleElement().isInstanceOf(ScoringModelRequestContext.class);
    }

    @Test
    void should_notify_provided_chat_model_listeners_of_an_async_chat() throws Exception {
        ChatModelMock model = ChatModelMock.thatResponds("Hi");

        withProviders(() -> assertThat(model.chatAsync(CHAT_REQUEST)).succeedsWithin(5, SECONDS), recording());

        assertThat(RECEIVED).singleElement().isInstanceOf(ChatModelRequestContext.class);
    }

    @Test
    void should_notify_provided_chat_model_listeners_of_a_streaming_chat() throws Exception {
        StreamingChatModelMock model = StreamingChatModelMock.thatStreams("Hel", "lo");
        TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();

        withProviders(() -> model.chat(CHAT_REQUEST, handler), recording());

        assertThat(handler.get()).isNotNull();
        assertThat(RECEIVED).singleElement().isInstanceOf(ChatModelRequestContext.class);
    }

    @Test
    void should_notify_provided_chat_model_listeners_of_a_streaming_chat_publisher() throws Exception {
        StreamingChatModelMock model = StreamingChatModelMock.thatStreams("Hel", "lo");
        List<Flow.Publisher<ChatModelStreamingEvent>> publishers = new ArrayList<>();

        withProviders(() -> publishers.add(model.chat(CHAT_REQUEST)), recording());
        assertThat(subscribe(publishers.get(0))).succeedsWithin(5, SECONDS);

        assertThat(RECEIVED).singleElement().isInstanceOf(ChatModelRequestContext.class);
    }

    @Test
    void should_notify_provided_embedding_model_listeners() throws Exception {
        EmbeddingModelMock model = EmbeddingModelMock.thatEmbeds(1f, 2f, 3f);

        withProviders(() -> model.embed("hello"), recording());

        assertThat(RECEIVED).singleElement().isInstanceOf(EmbeddingModelRequestContext.class);
    }

    @Test
    void should_notify_provided_embedding_model_listeners_of_an_async_embedding() throws Exception {
        EmbeddingModelMock model = EmbeddingModelMock.thatEmbeds(1f, 2f, 3f);

        withProviders(
                () -> assertThat(model.embedAsync(EmbeddingRequest.builder().input("hello").build()))
                        .succeedsWithin(5, SECONDS),
                recording());

        assertThat(RECEIVED).singleElement().isInstanceOf(EmbeddingModelRequestContext.class);
    }

    @Test
    void should_notify_provided_embedding_model_listeners_of_a_model_that_notifies_its_listeners_itself()
            throws Exception {
        EmbeddingModelMock model = EmbeddingModelMock.thatEmbeds(1f, 2f, 3f);
        List<TextSegment> segments = List.of(TextSegment.from("hello"));

        withProviders(
                () -> EmbeddingModelListenerUtils.withListeners(
                        model, segments, () -> Response.from(List.of(Embedding.from(new float[] {1f})))),
                recording());

        assertThat(RECEIVED).singleElement().isInstanceOf(EmbeddingModelRequestContext.class);
    }

    @Test
    void should_notify_provided_moderation_model_listeners() throws Exception {
        ModerationModelMock model = ModerationModelMock.thatNeverFlags();

        withProviders(() -> model.moderate("hello"), recording());

        assertThat(RECEIVED).singleElement().isInstanceOf(ModerationModelRequestContext.class);
    }

    @Test
    void should_notify_provided_scoring_model_listeners_of_an_async_scoring() throws Exception {
        ScoringModelMock model = ScoringModelMock.thatScores(document -> 0.5);

        withProviders(
                () -> assertThat(model.scoreAsync(ScoringRequest.builder()
                                .documents(List.of("a document"))
                                .query("a query")
                                .build()))
                        .succeedsWithin(5, SECONDS),
                recording());

        assertThat(RECEIVED).singleElement().isInstanceOf(ScoringModelRequestContext.class);
    }

    @Test
    void should_notify_provided_decision_model_listeners() throws Exception {
        DecisionModelMock model = DecisionModelMock.thatAnswersYesNoQuestions(question -> 0.9);

        withProviders(() -> model.decide(DECISION_REQUEST), recording());

        assertThat(RECEIVED).singleElement().isInstanceOf(DecisionModelRequestContext.class);
    }

    @Test
    void should_notify_provided_decision_model_listeners_of_an_async_decision() throws Exception {
        DecisionModelMock model = DecisionModelMock.thatAnswersYesNoQuestions(question -> 0.9);

        withProviders(() -> assertThat(model.decideAsync(DECISION_REQUEST)).succeedsWithin(5, SECONDS), recording());

        assertThat(RECEIVED).singleElement().isInstanceOf(DecisionModelRequestContext.class);
    }

    @Test
    void should_not_change_the_model_listeners_when_nothing_is_provided() {
        List<ChatModelListener> listeners = List.of(new ChatModelListener() {});

        assertThat(ObservabilityListeners.chat(listeners)).isSameAs(listeners);
    }

    @Test
    void should_not_change_the_listeners_when_a_provider_provides_none() throws Exception {
        List<ChatModelListener> chat = List.of(new ChatModelListener() {});
        List<EmbeddingModelListener> embedding = List.of(new EmbeddingModelListener() {});
        List<ModerationModelListener> moderation = List.of(new ModerationModelListener() {});
        List<ScoringModelListener> scoring = List.of(new ScoringModelListener() {});
        List<DecisionModelListener> decision = List.of(new DecisionModelListener() {});
        List<AiServiceListener<?>> registered = new ArrayList<>();
        AiServiceListenerRegistrar registrar = new DefaultAiServiceListenerRegistrar() {
            @Override
            public <T extends AiServiceEvent> void register(AiServiceListener<T> listener) {
                registered.add(listener);
            }
        };

        withProviders(
                () -> {
                    ObservabilityListeners.registerWith(registrar);

                    assertThat(ObservabilityListeners.chat(chat)).isSameAs(chat);
                    assertThat(ObservabilityListeners.embedding(embedding)).isSameAs(embedding);
                    assertThat(ObservabilityListeners.moderation(moderation)).isSameAs(moderation);
                    assertThat(ObservabilityListeners.scoring(scoring)).isSameAs(scoring);
                    assertThat(ObservabilityListeners.decision(decision)).isSameAs(decision);
                },
                TestObservabilityListenersProviders.ProvidingNothing.class.getName());

        assertThat(registered).isEmpty();
    }

    @Test
    void should_load_the_providers_of_a_class_loader_each_time() throws Exception {
        try (URLClassLoader classLoader = classLoaderWith(recording())) {
            assertThat(loadedBy(classLoader))
                    .singleElement()
                    .isInstanceOf(TestObservabilityListenersProviders.Recording.class);

            Files.delete(servicesFile());

            assertThat(loadedBy(classLoader)).isEmpty();
        }
    }

    @Test
    void should_use_only_the_providers_of_its_own_class_loader() throws Exception {
        AiServiceStartedEvent event = startedEvent();
        Thread thread = Thread.currentThread();
        ClassLoader original = thread.getContextClassLoader();
        try (URLClassLoader classLoader = classLoaderWith(recording())) {
            thread.setContextClassLoader(classLoader);

            AiServiceListenerRegistrar.newInstance().fireEvent(event);
            ChatModelMock.thatResponds("Hi").chat(CHAT_REQUEST);
        } finally {
            thread.setContextClassLoader(original);
        }

        assertThat(RECEIVED).isEmpty();
    }

    @Test
    void should_use_the_providers_of_a_framework_in_addition_to_its_own() throws Exception {
        AiServiceStartedEvent event = startedEvent();
        try (URLClassLoader classLoader = classLoaderWith(recording())) {
            ObservabilityListeners.useFrameworkProviders(loadedBy(classLoader));

            AiServiceListenerRegistrar.newInstance().fireEvent(event);
            ChatModelMock.thatResponds("Hi").chat(CHAT_REQUEST);
        } finally {
            ObservabilityListeners.useFrameworkProviders(List.of());
        }

        assertThat(RECEIVED).hasSize(2);
        assertThat(RECEIVED.get(0)).isSameAs(event);
        assertThat(RECEIVED.get(1)).isInstanceOf(ChatModelRequestContext.class);
    }

    @Test
    void should_stop_using_the_providers_of_a_framework_once_it_replaced_them() throws Exception {
        try (URLClassLoader classLoader = classLoaderWith(recording())) {
            ObservabilityListeners.useFrameworkProviders(loadedBy(classLoader));
            ObservabilityListeners.useFrameworkProviders(List.of());

            AiServiceListenerRegistrar.newInstance().fireEvent(startedEvent());
            ChatModelMock.thatResponds("Hi").chat(CHAT_REQUEST);
        }

        assertThat(RECEIVED).isEmpty();
    }

    @Test
    void should_use_a_provider_found_by_a_framework_and_in_its_own_class_loader_once() {
        List<ObservabilityListenersProvider> own = ObservabilityListeners.load(getClass().getClassLoader());
        TestObservabilityListenersProviders.active = List.of(new TestObservabilityListenersProviders.Recording());
        try {
            ObservabilityListeners.useFrameworkProviders(own);

            ChatModelMock.thatResponds("Hi").chat(CHAT_REQUEST);
        } finally {
            ObservabilityListeners.useFrameworkProviders(List.of());
            TestObservabilityListenersProviders.active = List.of();
        }

        assertThat(RECEIVED).hasSize(1);
    }

    @Test
    void should_skip_providers_outside_of_langchain4j() throws Exception {
        AiServiceStartedEvent event = startedEvent();

        withProviders(
                () -> AiServiceListenerRegistrar.newInstance().fireEvent(event),
                ProviderOutsideLangChain4j.class.getName());

        assertThat(RECEIVED).isEmpty();
    }

    @Test
    void should_use_the_providers_of_langchain4j_next_to_skipped_ones() throws Exception {
        AiServiceStartedEvent event = startedEvent();

        withProviders(
                () -> AiServiceListenerRegistrar.newInstance().fireEvent(event),
                ProviderOutsideLangChain4j.class.getName(),
                recording());

        assertThat(RECEIVED).containsExactly(event);
    }

    private static CompletableFuture<Void> subscribe(Flow.Publisher<ChatModelStreamingEvent> publisher) {
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

    private static String recording() {
        return TestObservabilityListenersProviders.Recording.class.getName();
    }

    private static AiServiceStartedEvent startedEvent() {
        return AiServiceStartedEvent.builder()
                .invocationContext(InvocationContext.builder()
                        .invocationId(UUID.randomUUID())
                        .timestampNow()
                        .build())
                .userMessage(UserMessage.from("Hello"))
                .build();
    }

    private Path servicesFile() {
        return directory.resolve("META-INF/services/" + ObservabilityListenersProvider.class.getName());
    }

    private void withProviders(Runnable action, String... providerClassNames) throws Exception {
        try (URLClassLoader classLoader = classLoaderWith(providerClassNames)) {
            TestObservabilityListenersProviders.active = loadedBy(classLoader);
            action.run();
        } finally {
            TestObservabilityListenersProviders.active = List.of();
        }
    }

    private static List<ObservabilityListenersProvider> loadedBy(ClassLoader classLoader) {
        // the parent class loader also finds the Switchable provider of the test resources
        return ObservabilityListeners.load(classLoader).stream()
                .filter(provider -> !(provider instanceof TestObservabilityListenersProviders.Switchable))
                .toList();
    }

    private URLClassLoader classLoaderWith(String... providerClassNames) throws IOException {
        Files.createDirectories(servicesFile().getParent());
        Files.writeString(servicesFile(), String.join("\n", providerClassNames));
        return new URLClassLoader(new URL[] {directory.toUri().toURL()}, getClass().getClassLoader());
    }
}
