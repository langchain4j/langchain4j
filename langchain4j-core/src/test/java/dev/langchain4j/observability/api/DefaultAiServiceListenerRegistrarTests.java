package dev.langchain4j.observability.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNoException;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.guardrail.ChatExecutor;
import dev.langchain4j.guardrail.GuardrailRequestParams;
import dev.langchain4j.guardrail.InputGuardrail;
import dev.langchain4j.guardrail.InputGuardrailRequest;
import dev.langchain4j.guardrail.InputGuardrailResult;
import dev.langchain4j.guardrail.OutputGuardrail;
import dev.langchain4j.guardrail.OutputGuardrailRequest;
import dev.langchain4j.guardrail.OutputGuardrailResult;
import dev.langchain4j.guardrail.ToolInputGuardrail;
import dev.langchain4j.guardrail.ToolInputGuardrailRequest;
import dev.langchain4j.guardrail.ToolInputGuardrailResult;
import dev.langchain4j.guardrail.ToolOutputGuardrail;
import dev.langchain4j.guardrail.ToolOutputGuardrailRequest;
import dev.langchain4j.guardrail.ToolOutputGuardrailResult;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.observability.api.event.AiServiceCancelledEvent;
import dev.langchain4j.observability.api.event.AiServiceCompletedEvent;
import dev.langchain4j.observability.api.event.AiServiceErrorEvent;
import dev.langchain4j.observability.api.event.AiServiceEvent;
import dev.langchain4j.observability.api.event.AiServiceInvocationSummaryEvent;
import dev.langchain4j.observability.api.event.AiServiceRequestIssuedEvent;
import dev.langchain4j.observability.api.event.AiServiceResponseReceivedEvent;
import dev.langchain4j.observability.api.event.AiServiceStartedEvent;
import dev.langchain4j.observability.api.event.InputGuardrailExecutedEvent;
import dev.langchain4j.observability.api.event.OutputGuardrailExecutedEvent;
import dev.langchain4j.observability.api.event.RetrievalAugmentedEvent;
import dev.langchain4j.observability.api.event.ToolExecutedEvent;
import dev.langchain4j.observability.api.event.ToolInputGuardrailExecutedEvent;
import dev.langchain4j.observability.api.event.ToolOutputGuardrailExecutedEvent;
import dev.langchain4j.observability.api.listener.AiServiceCancelledListener;
import dev.langchain4j.observability.api.listener.AiServiceCompletedListener;
import dev.langchain4j.observability.api.listener.AiServiceErrorListener;
import dev.langchain4j.observability.api.listener.AiServiceInvocationSummaryListener;
import dev.langchain4j.observability.api.listener.AiServiceListener;
import dev.langchain4j.observability.api.listener.AiServiceRequestIssuedListener;
import dev.langchain4j.observability.api.listener.AiServiceResponseReceivedListener;
import dev.langchain4j.observability.api.listener.AiServiceStartedListener;
import dev.langchain4j.observability.api.listener.InputGuardrailExecutedListener;
import dev.langchain4j.observability.api.listener.OutputGuardrailExecutedListener;
import dev.langchain4j.observability.api.listener.RetrievalAugmentedListener;
import dev.langchain4j.observability.api.listener.ToolExecutedEventListener;
import dev.langchain4j.observability.api.listener.ToolInputGuardrailExecutedListener;
import dev.langchain4j.observability.api.listener.ToolOutputGuardrailExecutedListener;
import java.lang.ref.WeakReference;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class DefaultAiServiceListenerRegistrarTests {

    private static final AiServiceListenerRegistrar REGISTRAR = AiServiceListenerRegistrar.newInstance();

    private static final InvocationContext DEFAULT_INVOCATION_CONTEXT = InvocationContext.builder()
            .invocationId(UUID.randomUUID())
            .interfaceName("SomeInterface")
            .methodName("someMethod")
            .methodArgument("one")
            .methodArgument("two")
            .chatMemoryId("one")
            .build();

    private static final AiServiceRequestIssuedEvent REQUEST_ISSUED_EVENT = AiServiceRequestIssuedEvent.builder()
            .invocationContext(DEFAULT_INVOCATION_CONTEXT)
            .request(ChatRequest.builder()
                    .messages(List.of(UserMessage.from("Hi!")))
                    .build())
            .build();

    private static final AiServiceResponseReceivedEvent RESPONSE_RECEIVED_EVENT =
            AiServiceResponseReceivedEvent.builder()
                    .invocationContext(DEFAULT_INVOCATION_CONTEXT)
                    .request(ChatRequest.builder()
                            .messages(List.of(UserMessage.from("Hi!")))
                            .build())
                    .response(ChatResponse.builder()
                            .aiMessage(AiMessage.from("Message!"))
                            .build())
                    .build();

    private static final AiServiceErrorEvent INVOCATION_ERROR_EVENT = AiServiceErrorEvent.builder()
            .invocationContext(DEFAULT_INVOCATION_CONTEXT)
            .error(new RuntimeException("Some error"))
            .build();

    private static final AiServiceCancelledEvent INVOCATION_CANCELLED_EVENT = AiServiceCancelledEvent.builder()
            .invocationContext(DEFAULT_INVOCATION_CONTEXT)
            .build();

    private static final AiServiceCompletedEvent INVOCATION_COMPLETED_EVENT = AiServiceCompletedEvent.builder()
            .invocationContext(DEFAULT_INVOCATION_CONTEXT)
            .build();

    private static final AiServiceStartedEvent INVOCATION_STARTED_EVENT = AiServiceStartedEvent.builder()
            .invocationContext(DEFAULT_INVOCATION_CONTEXT)
            .userMessage(UserMessage.from("Hello, world!"))
            .build();

    private static final OutputGuardrailExecutedEvent OUTPUT_GUARDRAIL_EXECUTED_EVENT =
            OutputGuardrailExecutedEvent.builder()
                    .invocationContext(DEFAULT_INVOCATION_CONTEXT)
                    .guardrailClass(OG.class)
                    .request(OutputGuardrailRequest.builder()
                            .responseFromLLM(ChatResponse.builder()
                                    .aiMessage(AiMessage.from("Message!"))
                                    .build())
                            .requestParams(GuardrailRequestParams.builder()
                                    .userMessageTemplate("")
                                    .variables(Map.of())
                                    .invocationContext(DEFAULT_INVOCATION_CONTEXT)
                                    .aiServiceListenerRegistrar(REGISTRAR)
                                    .build())
                            .chatExecutor(new ChatExecutor() {
                                @Override
                                public ChatResponse execute() {
                                    return execute(List.of());
                                }

                                @Override
                                public ChatResponse execute(List<ChatMessage> chatMessages) {
                                    return ChatResponse.builder()
                                            .aiMessage(AiMessage.from("Message!"))
                                            .build();
                                }
                            })
                            .build())
                    .result(OutputGuardrailResult.success())
                    .duration(Duration.ofMillis(100))
                    .build();

    private static final InputGuardrailExecutedEvent INPUT_GUARDRAIL_EXECUTED_EVENT =
            InputGuardrailExecutedEvent.builder()
                    .invocationContext(DEFAULT_INVOCATION_CONTEXT)
                    .guardrailClass(IG.class)
                    .request(InputGuardrailRequest.builder()
                            .userMessage(UserMessage.from("Hello, world!"))
                            .commonParams(GuardrailRequestParams.builder()
                                    .userMessageTemplate("")
                                    .variables(Map.of())
                                    .invocationContext(DEFAULT_INVOCATION_CONTEXT)
                                    .aiServiceListenerRegistrar(REGISTRAR)
                                    .build())
                            .build())
                    .result(InputGuardrailResult.success())
                    .duration(Duration.ofMillis(50))
                    .build();

    private static final GuardrailRequestParams TOOL_GUARDRAIL_REQUEST_PARAMS = GuardrailRequestParams.builder()
            .userMessageTemplate("")
            .variables(Map.of())
            .invocationContext(DEFAULT_INVOCATION_CONTEXT)
            .aiServiceListenerRegistrar(REGISTRAR)
            .build();

    private static final ToolInputGuardrailExecutedEvent TOOL_INPUT_GUARDRAIL_EXECUTED_EVENT =
            ToolInputGuardrailExecutedEvent.builder()
                    .invocationContext(DEFAULT_INVOCATION_CONTEXT)
                    .guardrailClass(TIG.class)
                    .request(ToolInputGuardrailRequest.builder()
                            .toolExecutionRequest(
                                    ToolExecutionRequest.builder().name("tool").build())
                            .requestParams(TOOL_GUARDRAIL_REQUEST_PARAMS)
                            .build())
                    .result(ToolInputGuardrailResult.success())
                    .duration(Duration.ofMillis(10))
                    .build();

    private static final ToolOutputGuardrailExecutedEvent TOOL_OUTPUT_GUARDRAIL_EXECUTED_EVENT =
            ToolOutputGuardrailExecutedEvent.builder()
                    .invocationContext(DEFAULT_INVOCATION_CONTEXT)
                    .guardrailClass(TOG.class)
                    .request(ToolOutputGuardrailRequest.builder()
                            .toolExecutionRequest(
                                    ToolExecutionRequest.builder().name("tool").build())
                            .resultContents(List.of(TextContent.from("result")))
                            .requestParams(TOOL_GUARDRAIL_REQUEST_PARAMS)
                            .build())
                    .result(ToolOutputGuardrailResult.success())
                    .duration(Duration.ofMillis(20))
                    .build();

    private static final ToolExecutedEvent TOOL_EXECUTED_EVENT = ToolExecutedEvent.builder()
            .invocationContext(DEFAULT_INVOCATION_CONTEXT)
            .request(ToolExecutionRequest.builder().build())
            .resultText("Success!")
            .build();

    private static final List<AiServiceEvent> ALL_EVENTS = List.of(
            REQUEST_ISSUED_EVENT,
            RESPONSE_RECEIVED_EVENT,
            INVOCATION_ERROR_EVENT,
            INVOCATION_COMPLETED_EVENT,
            INVOCATION_STARTED_EVENT,
            OUTPUT_GUARDRAIL_EXECUTED_EVENT,
            INPUT_GUARDRAIL_EXECUTED_EVENT,
            TOOL_INPUT_GUARDRAIL_EXECUTED_EVENT,
            TOOL_OUTPUT_GUARDRAIL_EXECUTED_EVENT,
            TOOL_EXECUTED_EVENT);

    // Create 2 instances of each listener
    private static final List<AbstractTestEventListener<?>> ALL_LISTENERS = IntStream.range(0, 2)
            .mapToObj(i -> List.of(
                    new TestInputGuardrailListener(),
                    new TestOutputGuardrailListener(),
                    new TestToolInputGuardrailListener(),
                    new TestToolOutputGuardrailListener(),
                    new TestInvocationStartedListener(),
                    new TestInvocationCompletedListener(),
                    new TestInvocationErrorListener(),
                    new TestLLMResponseReceivedListener(),
                    new TestLLMRequestIssuedListener(),
                    new TestToolExecutedListener()))
            .flatMap(List::stream)
            .toList();

    @Test
    void shouldNotifyTheTypedCancelledAndRetrievalAugmentedListeners() {
        var registrar = AiServiceListenerRegistrar.newInstance();
        List<AiServiceEvent> received = new CopyOnWriteArrayList<>();
        registrar.register((AiServiceCancelledListener) received::add);
        registrar.register((RetrievalAugmentedListener) received::add);
        InvocationContext invocationContext =
                InvocationContext.builder().invocationId(UUID.randomUUID()).build();
        var retrievalAugmented = RetrievalAugmentedEvent.builder()
                .invocationContext(invocationContext)
                .chatMessage(UserMessage.from("Hello"))
                .contents(List.of())
                .build();
        var cancelled = AiServiceCancelledEvent.builder()
                .invocationContext(invocationContext)
                .build();

        registrar.fireEvent(retrievalAugmented);
        registrar.fireEvent(cancelled);

        assertThat(received).containsExactly(retrievalAugmented, cancelled);
    }

    @Test
    void registrarEatsThrownExceptionsDuringFiring() {
        // From https://github.com/langchain4j/langchain4j/issues/4499
        var registrar = AiServiceListenerRegistrar.newInstance();

        registrar.register((AiServiceStartedListener) event -> {
            throw new RuntimeException("Some error");
        });

        assertThatNoException().isThrownBy(() -> registrar.fireEvent(INVOCATION_STARTED_EVENT));
    }

    @Test
    void registrarDoesntEatThrownExceptionsDuringFiring() {
        // From https://github.com/langchain4j/langchain4j/issues/4499
        var registrar = AiServiceListenerRegistrar.newInstance(true);

        registrar.register((AiServiceStartedListener) event -> {
            throw new RuntimeException("Some error");
        });

        assertThatExceptionOfType(RuntimeException.class)
                .isThrownBy(() -> registrar.fireEvent(INVOCATION_STARTED_EVENT))
                .withMessage("Some error");
    }

    @Test
    void shouldFireInvocationSummaryEventOnCompletion() {
        // From https://github.com/langchain4j/langchain4j/issues/4207
        var registrar = AiServiceListenerRegistrar.newInstance();
        var listener = new TestAiServiceInvocationSummaryListener();

        registrar.register(listener);

        registrar.fireEvent(INVOCATION_STARTED_EVENT);
        registrar.fireEvent(REQUEST_ISSUED_EVENT);
        registrar.fireEvent(RESPONSE_RECEIVED_EVENT);
        registrar.fireEvent(INVOCATION_COMPLETED_EVENT);

        assertThat(listener.count()).isOne();
        assertThat(listener.lastEvent())
                .isNotNull()
                .extracting(AiServiceEvent::invocationContext, AiServiceInvocationSummaryEvent::events)
                .containsExactly(
                        DEFAULT_INVOCATION_CONTEXT,
                        List.of(
                                INVOCATION_STARTED_EVENT,
                                REQUEST_ISSUED_EVENT,
                                RESPONSE_RECEIVED_EVENT,
                                INVOCATION_COMPLETED_EVENT));
    }

    @Test
    void shouldNotNotifyAiServiceGuardrailListenersOfToolGuardrailEvents() {
        var registrar = AiServiceListenerRegistrar.newInstance();
        var inputListener = new TestInputGuardrailListener();
        var outputListener = new TestOutputGuardrailListener();
        var toolInputListener = new TestToolInputGuardrailListener();
        var toolOutputListener = new TestToolOutputGuardrailListener();
        List.of(inputListener, outputListener, toolInputListener, toolOutputListener)
                .forEach(registrar::register);

        registrar.fireEvent(TOOL_INPUT_GUARDRAIL_EXECUTED_EVENT);
        registrar.fireEvent(TOOL_OUTPUT_GUARDRAIL_EXECUTED_EVENT);

        assertThat(inputListener.count()).isZero();
        assertThat(outputListener.count()).isZero();
        assertThat(toolInputListener.lastEvent()).isSameAs(TOOL_INPUT_GUARDRAIL_EXECUTED_EVENT);
        assertThat(toolOutputListener.lastEvent()).isSameAs(TOOL_OUTPUT_GUARDRAIL_EXECUTED_EVENT);
    }

    @Test
    void shouldIncludeToolGuardrailEventsInTheInvocationSummaryEvent() {
        var registrar = AiServiceListenerRegistrar.newInstance();
        var listener = new TestAiServiceInvocationSummaryListener();
        registrar.register(listener);

        registrar.fireEvent(INVOCATION_STARTED_EVENT);
        registrar.fireEvent(TOOL_INPUT_GUARDRAIL_EXECUTED_EVENT);
        registrar.fireEvent(TOOL_OUTPUT_GUARDRAIL_EXECUTED_EVENT);
        registrar.fireEvent(INVOCATION_COMPLETED_EVENT);

        assertThat(listener.lastEvent().events())
                .containsExactly(
                        INVOCATION_STARTED_EVENT,
                        TOOL_INPUT_GUARDRAIL_EXECUTED_EVENT,
                        TOOL_OUTPUT_GUARDRAIL_EXECUTED_EVENT,
                        INVOCATION_COMPLETED_EVENT);
    }

    @Test
    void shouldFireInvocationSummaryEventOnError() {
        // From https://github.com/langchain4j/langchain4j/issues/4207
        var registrar = AiServiceListenerRegistrar.newInstance();
        var listener = new TestAiServiceInvocationSummaryListener();

        registrar.register(listener);

        registrar.fireEvent(INVOCATION_STARTED_EVENT);
        registrar.fireEvent(REQUEST_ISSUED_EVENT);
        registrar.fireEvent(INVOCATION_ERROR_EVENT);

        assertThat(listener.count()).isOne();
        assertThat(listener.lastEvent())
                .isNotNull()
                .extracting(AiServiceInvocationSummaryEvent::events)
                .isEqualTo(List.of(INVOCATION_STARTED_EVENT, REQUEST_ISSUED_EVENT, INVOCATION_ERROR_EVENT));
    }

    @Test
    void shouldFireInvocationSummaryEventAfterTheEventThatEndedTheInvocation() {
        var registrar = AiServiceListenerRegistrar.newInstance();
        var notified = new CopyOnWriteArrayList<Class<?>>();

        registrar.register((AiServiceCompletedListener) event -> notified.add(event.eventClass()));
        registrar.register((AiServiceInvocationSummaryListener) event -> notified.add(event.eventClass()));

        registrar.fireEvent(INVOCATION_STARTED_EVENT);
        registrar.fireEvent(INVOCATION_COMPLETED_EVENT);

        assertThat(notified).containsExactly(AiServiceCompletedEvent.class, AiServiceInvocationSummaryEvent.class);
    }

    @Test
    void shouldRejectExternallyFiredInvocationSummaryEvent() {
        var registrar = AiServiceListenerRegistrar.newInstance();
        var invocationSummaryEvent = AiServiceInvocationSummaryEvent.builder()
                .invocationContext(DEFAULT_INVOCATION_CONTEXT)
                .events(List.of(INVOCATION_STARTED_EVENT, INVOCATION_COMPLETED_EVENT))
                .build();

        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> registrar.fireEvent(invocationSummaryEvent))
                .withMessageContaining("composite event");
    }

    @Test
    void shouldNotFireInvocationSummaryEventBeforeCompletionOrError() {
        // From https://github.com/langchain4j/langchain4j/issues/4207
        var registrar = AiServiceListenerRegistrar.newInstance();
        var listener = new TestAiServiceInvocationSummaryListener();

        registrar.register(listener);

        registrar.fireEvent(INVOCATION_STARTED_EVENT);
        registrar.fireEvent(REQUEST_ISSUED_EVENT);
        registrar.fireEvent(RESPONSE_RECEIVED_EVENT);

        assertThat(listener)
                .extracting(
                        TestAiServiceInvocationSummaryListener::count,
                        TestAiServiceInvocationSummaryListener::lastEvent)
                .containsExactly(0, null);
    }

    @Test
    void shouldNotTrackInvocationsStartedWithoutAnInvocationSummaryListener() {
        var registrar = AiServiceListenerRegistrar.newInstance();
        var listener = new TestAiServiceInvocationSummaryListener();

        registrar.fireEvent(INVOCATION_STARTED_EVENT);

        // Registering the listener part way through the invocation doesn't start tracking it
        registrar.register(listener);
        registrar.fireEvent(REQUEST_ISSUED_EVENT);
        registrar.fireEvent(INVOCATION_COMPLETED_EVENT);

        assertThat(listener.count()).isZero();
    }

    @Test
    void shouldNotFireInvocationSummaryEventForAnInvocationThatNeverStarted() {
        var registrar = AiServiceListenerRegistrar.newInstance();
        var listener = new TestAiServiceInvocationSummaryListener();

        registrar.register(listener);

        registrar.fireEvent(REQUEST_ISSUED_EVENT);
        registrar.fireEvent(INVOCATION_ERROR_EVENT);

        assertThat(listener.count()).isZero();
    }

    @Test
    void shouldNotTrackEventsWithoutAnInvocationId() {
        var registrar = AiServiceListenerRegistrar.newInstance();
        var listener = new TestAiServiceInvocationSummaryListener();
        var invocationContext =
                DEFAULT_INVOCATION_CONTEXT.toBuilder().invocationId(null).build();

        registrar.register(listener);

        registrar.fireEvent(INVOCATION_STARTED_EVENT.toBuilder()
                .invocationContext(invocationContext)
                .build());
        registrar.fireEvent(INVOCATION_COMPLETED_EVENT.toBuilder()
                .invocationContext(invocationContext)
                .build());

        assertThat(listener.count()).isZero();
    }

    @Test
    void shouldIgnoreEventsFiredAfterTheInvocationEnded() {
        var registrar = AiServiceListenerRegistrar.newInstance();
        var listener = new TestAiServiceInvocationSummaryListener();

        registrar.register(listener);

        registrar.fireEvent(INVOCATION_STARTED_EVENT);
        registrar.fireEvent(INVOCATION_COMPLETED_EVENT);

        // A late event must neither start tracking a new invocation nor be held on to
        registrar.fireEvent(TOOL_EXECUTED_EVENT);
        registrar.fireEvent(INVOCATION_CANCELLED_EVENT);

        assertThat(listener.count()).isOne();
        assertThat(listener.lastEvent().events()).containsExactly(INVOCATION_STARTED_EVENT, INVOCATION_COMPLETED_EVENT);
    }

    @Test
    void shouldFireInvocationSummaryEventWithTheCollectedEventsWhenTheInvocationIsCanceled() {
        // e.g. an asynchronous or streaming invocation that was canceled
        var registrar = AiServiceListenerRegistrar.newInstance();
        var listener = new TestAiServiceInvocationSummaryListener();

        registrar.register(listener);

        registrar.fireEvent(INVOCATION_STARTED_EVENT);
        registrar.fireEvent(REQUEST_ISSUED_EVENT);
        registrar.fireEvent(INVOCATION_CANCELLED_EVENT);

        // A late end event doesn't fire another invocation summary event
        registrar.fireEvent(INVOCATION_CANCELLED_EVENT);
        registrar.fireEvent(INVOCATION_ERROR_EVENT);

        assertThat(listener.count()).isOne();
        assertThat(listener.lastEvent())
                .isNotNull()
                .extracting(AiServiceEvent::invocationContext, AiServiceInvocationSummaryEvent::events)
                .containsExactly(
                        DEFAULT_INVOCATION_CONTEXT,
                        List.of(INVOCATION_STARTED_EVENT, REQUEST_ISSUED_EVENT, INVOCATION_CANCELLED_EVENT));
    }

    @Test
    void aLateCancellationHasNoEffectOnACompletedInvocation() {
        var registrar = AiServiceListenerRegistrar.newInstance();
        var listener = new TestAiServiceInvocationSummaryListener();

        registrar.register(listener);

        registrar.fireEvent(INVOCATION_STARTED_EVENT);
        registrar.fireEvent(INVOCATION_COMPLETED_EVENT);
        registrar.fireEvent(INVOCATION_CANCELLED_EVENT);

        assertThat(listener.count()).isOne();
        assertThat(listener.lastEvent().events()).containsExactly(INVOCATION_STARTED_EVENT, INVOCATION_COMPLETED_EVENT);
    }

    @Test
    void shouldStopTrackingTheInvocationEvenIfAListenerFails() {
        var registrar = AiServiceListenerRegistrar.newInstance(true);
        var listener = new TestAiServiceInvocationSummaryListener();

        registrar.register(listener);
        registrar.register((AiServiceCompletedListener) event -> {
            throw new RuntimeException("Some error");
        });

        registrar.fireEvent(INVOCATION_STARTED_EVENT);
        assertThatExceptionOfType(RuntimeException.class)
                .isThrownBy(() -> registrar.fireEvent(INVOCATION_COMPLETED_EVENT))
                .withMessage("Some error");

        // The invocation is no longer tracked, so a late end event has no effect
        registrar.fireEvent(INVOCATION_CANCELLED_EVENT);

        assertThat(listener.count()).isZero();
    }

    @Test
    void shouldKeepConcurrentInvocationsSeparate() throws InterruptedException {
        var registrar = AiServiceListenerRegistrar.newInstance();
        var summaries = new CopyOnWriteArrayList<AiServiceInvocationSummaryEvent>();
        var invocationCount = 50;
        var start = new CountDownLatch(1);

        registrar.register((AiServiceInvocationSummaryListener) summaries::add);

        // ExecutorService isn't AutoCloseable on Java 17
        var executor = Executors.newFixedThreadPool(8);

        try {
            IntStream.range(0, invocationCount)
                    .forEach(i -> executor.submit(() -> {
                        var invocationContext = DEFAULT_INVOCATION_CONTEXT.toBuilder()
                                .invocationId(UUID.randomUUID())
                                .build();

                        start.await();
                        registrar.fireEvent(INVOCATION_STARTED_EVENT.toBuilder()
                                .invocationContext(invocationContext)
                                .build());
                        registrar.fireEvent(REQUEST_ISSUED_EVENT.toBuilder()
                                .invocationContext(invocationContext)
                                .build());
                        registrar.fireEvent(RESPONSE_RECEIVED_EVENT.toBuilder()
                                .invocationContext(invocationContext)
                                .build());
                        registrar.fireEvent(INVOCATION_COMPLETED_EVENT.toBuilder()
                                .invocationContext(invocationContext)
                                .build());
                        return null;
                    }));

            start.countDown();
            executor.shutdown();
            assertThat(executor.awaitTermination(1, TimeUnit.MINUTES)).isTrue();
        } finally {
            executor.shutdownNow();
        }

        assertThat(summaries)
                .hasSize(invocationCount)
                .allSatisfy(summary -> assertThat(summary.events())
                        .allSatisfy(
                                event -> assertThat(event.invocationContext()).isEqualTo(summary.invocationContext()))
                        .<Class<?>>map(AiServiceEvent::eventClass)
                        .containsExactly(
                                AiServiceStartedEvent.class,
                                AiServiceRequestIssuedEvent.class,
                                AiServiceResponseReceivedEvent.class,
                                AiServiceCompletedEvent.class))
                .extracting(summary -> summary.invocationContext().invocationId())
                .doesNotHaveDuplicates();
    }

    enum InvocationEnd {
        COMPLETED,
        ERROR,
        CANCELLED
    }

    @ParameterizedTest
    @EnumSource(InvocationEnd.class)
    void shouldNestAChildFromAnotherRegistrarWithoutAChildListener(InvocationEnd end) {
        var parentRegistrar = new DefaultAiServiceListenerRegistrar();
        var childRegistrar = new DefaultAiServiceListenerRegistrar();
        var summaries = new CopyOnWriteArrayList<AiServiceInvocationSummaryEvent>();
        parentRegistrar.register((AiServiceInvocationSummaryListener) summaries::add);
        InvocationContext parent = nestedContext(null);
        InvocationContext child = nestedContext(parent);
        var parentStarted = started(parent);
        var childStarted = started(child);
        AiServiceEvent childEnd =
                switch (end) {
                    case COMPLETED -> completed(child);
                    case ERROR ->
                        AiServiceErrorEvent.builder()
                                .invocationContext(child)
                                .error(new IllegalStateException("child failure"))
                                .build();
                    case CANCELLED ->
                        AiServiceCancelledEvent.builder()
                                .invocationContext(child)
                                .build();
                };
        var parentEnd = completed(parent);

        parentRegistrar.fireEvent(parentStarted);
        childRegistrar.fireEvent(childStarted);
        childRegistrar.fireEvent(childEnd);
        parentRegistrar.fireEvent(parentEnd);

        assertThat(summaries).hasSize(1);
        AiServiceInvocationSummaryEvent nested =
                (AiServiceInvocationSummaryEvent) summaries.get(0).events().get(1);
        assertThat(summaries.get(0).events()).containsExactly(parentStarted, nested, parentEnd);
        assertThat(nested.invocationContext()).isEqualTo(child);
        assertThat(nested.events()).containsExactly(childStarted, childEnd);
    }

    @Test
    void shouldCollectMultipleLevelsWithOnlyARootListener() {
        var rootRegistrar = new DefaultAiServiceListenerRegistrar();
        var childRegistrar = new DefaultAiServiceListenerRegistrar();
        var leafRegistrar = new DefaultAiServiceListenerRegistrar();
        var summaries = new CopyOnWriteArrayList<AiServiceInvocationSummaryEvent>();
        rootRegistrar.register((AiServiceInvocationSummaryListener) summaries::add);
        InvocationContext root = nestedContext(null);
        InvocationContext child = nestedContext(root);
        InvocationContext leaf = nestedContext(child);

        rootRegistrar.fireEvent(started(root));
        childRegistrar.fireEvent(started(child));
        leafRegistrar.fireEvent(started(leaf));
        leafRegistrar.fireEvent(completed(leaf));
        childRegistrar.fireEvent(completed(child));
        rootRegistrar.fireEvent(completed(root));

        assertThat(summaries).hasSize(1);
        var childSummary =
                (AiServiceInvocationSummaryEvent) summaries.get(0).events().get(1);
        var leafSummary =
                (AiServiceInvocationSummaryEvent) childSummary.events().get(1);
        assertThat(childSummary.invocationContext()).isEqualTo(child);
        assertThat(leafSummary.invocationContext()).isEqualTo(leaf);
    }

    @Test
    void shouldNotModifyAFinishedParentWhenAChildEndsLater() {
        var registrar = new DefaultAiServiceListenerRegistrar();
        var summaries = new CopyOnWriteArrayList<AiServiceInvocationSummaryEvent>();
        registrar.register((AiServiceInvocationSummaryListener) summaries::add);
        InvocationContext parent = nestedContext(null);
        InvocationContext child = nestedContext(parent);

        registrar.fireEvent(started(parent));
        registrar.fireEvent(started(child));
        registrar.fireEvent(completed(parent));
        registrar.fireEvent(completed(child));

        assertThat(summaries).hasSize(2);
        assertThat(summaries.get(0).events()).noneMatch(AiServiceInvocationSummaryEvent.class::isInstance);
        assertThat(summaries.get(1).invocationContext()).isEqualTo(child);
    }

    @Test
    void shouldNotCollectAChildWithoutAListenerThatStartsAfterItsParentEnded() {
        var parentRegistrar = new DefaultAiServiceListenerRegistrar();
        parentRegistrar.register((AiServiceInvocationSummaryListener) event -> {});
        InvocationContext parent = nestedContext(null);
        parentRegistrar.fireEvent(started(parent));
        parentRegistrar.fireEvent(completed(parent));

        var childRegistrar = new DefaultAiServiceListenerRegistrar();
        InvocationContext lateChild = nestedContext(parent);
        childRegistrar.fireEvent(started(lateChild));
        var lateSummaries = new CopyOnWriteArrayList<AiServiceInvocationSummaryEvent>();
        childRegistrar.register((AiServiceInvocationSummaryListener) lateSummaries::add);
        childRegistrar.fireEvent(completed(lateChild));

        assertThat(lateSummaries).isEmpty();
    }

    @Test
    void shouldRemoveTheParentRegistrationEvenIfATerminalListenerThrows() {
        var parentRegistrar = new DefaultAiServiceListenerRegistrar();
        parentRegistrar.shouldThrowExceptionOnEventError(true);
        parentRegistrar.register((AiServiceInvocationSummaryListener) event -> {});
        parentRegistrar.register((AiServiceCompletedListener) event -> {
            throw new IllegalStateException("listener failure");
        });
        InvocationContext parent = nestedContext(null);
        parentRegistrar.fireEvent(started(parent));
        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> parentRegistrar.fireEvent(completed(parent)))
                .withMessage("listener failure");

        var childRegistrar = new DefaultAiServiceListenerRegistrar();
        InvocationContext child = nestedContext(parent);
        childRegistrar.fireEvent(started(child));
        var summaries = new CopyOnWriteArrayList<AiServiceInvocationSummaryEvent>();
        childRegistrar.register((AiServiceInvocationSummaryListener) summaries::add);
        childRegistrar.fireEvent(completed(child));

        assertThat(summaries).isEmpty();
    }

    @Test
    void shouldNotKeepTheRegistrarOfAnInvocationThatNeverEndsAlive() throws InterruptedException {
        WeakReference<DefaultAiServiceListenerRegistrar> registrar = registrarOfAnInvocationThatNeverEnds();

        for (int attempt = 0; attempt < 50 && registrar.get() != null; attempt++) {
            System.gc();
            Thread.sleep(10);
        }

        assertThat(registrar.get()).isNull();
    }

    @Test
    void hasCorrectListeners() {
        var registrar = (DefaultAiServiceListenerRegistrar) assertThat(REGISTRAR)
                .isNotNull()
                .isExactlyInstanceOf(DefaultAiServiceListenerRegistrar.class)
                .actual();

        // Assert our starting point that nothing has happened
        assertListenersNotExecuted();

        // Register all the listeners
        ALL_LISTENERS.forEach(registrar::register);

        // Fire the events
        ALL_EVENTS.forEach(registrar::fireEvent);

        // Ensure that all the listeners have been executed
        assertThat(ALL_LISTENERS)
                .allSatisfy(l -> assertThat(l)
                        .isNotNull()
                        .satisfies(
                                el -> assertThat(el.count()).isOne(),
                                el -> assertThat(el.lastEvent())
                                        .isNotNull()
                                        .extracting(AiServiceEvent::invocationContext)
                                        .usingRecursiveComparison()
                                        .isEqualTo(DEFAULT_INVOCATION_CONTEXT)));

        // Unregister all the listeners & reset their data
        ALL_LISTENERS.forEach(l -> {
            registrar.unregister(l);
            l.reset();
        });

        // Fire the events (no listeners should be there)
        ALL_EVENTS.forEach(registrar::fireEvent);

        // We're back at our starting point
        assertListenersNotExecuted();
    }

    private static WeakReference<DefaultAiServiceListenerRegistrar> registrarOfAnInvocationThatNeverEnds() {
        var registrar = new DefaultAiServiceListenerRegistrar();
        registrar.register((AiServiceInvocationSummaryListener) event -> {});
        registrar.fireEvent(started(nestedContext(null)));
        return new WeakReference<>(registrar);
    }

    private static InvocationContext nestedContext(InvocationContext parent) {
        return InvocationContext.builder()
                .invocationId(UUID.randomUUID())
                .parentInvocationId(parent == null ? null : parent.invocationId())
                .build();
    }

    private static AiServiceStartedEvent started(InvocationContext context) {
        return AiServiceStartedEvent.builder()
                .invocationContext(context)
                .userMessage(UserMessage.from("question"))
                .build();
    }

    private static AiServiceCompletedEvent completed(InvocationContext context) {
        return AiServiceCompletedEvent.builder()
                .invocationContext(context)
                .result("answer")
                .build();
    }

    private static void assertListenersNotExecuted() {
        assertThat(ALL_LISTENERS)
                .isNotNull()
                .hasSize(10 * 2)
                .allSatisfy(l -> assertThat(l)
                        .isNotNull()
                        .satisfies(
                                el -> assertThat(el.count()).isZero(),
                                el -> assertThat(el.lastEvent()).isNull()));
    }

    private abstract static class AbstractTestEventListener<T extends AiServiceEvent> implements AiServiceListener<T> {

        private final AtomicInteger count = new AtomicInteger();
        private T lastEvent;

        @Override
        public void onEvent(T event) {
            this.count.incrementAndGet();
            this.lastEvent = event;
        }

        int count() {
            return this.count.get();
        }

        T lastEvent() {
            return this.lastEvent;
        }

        void reset() {
            this.count.set(0);
            this.lastEvent = null;
        }
    }

    private static class TestInputGuardrailListener extends AbstractTestEventListener<InputGuardrailExecutedEvent>
            implements InputGuardrailExecutedListener {}

    private static class TestOutputGuardrailListener extends AbstractTestEventListener<OutputGuardrailExecutedEvent>
            implements OutputGuardrailExecutedListener {}

    private static class TestToolInputGuardrailListener
            extends AbstractTestEventListener<ToolInputGuardrailExecutedEvent>
            implements ToolInputGuardrailExecutedListener {}

    private static class TestToolOutputGuardrailListener
            extends AbstractTestEventListener<ToolOutputGuardrailExecutedEvent>
            implements ToolOutputGuardrailExecutedListener {}

    private static class TestInvocationStartedListener extends AbstractTestEventListener<AiServiceStartedEvent>
            implements AiServiceStartedListener {}

    private static class TestInvocationCompletedListener extends AbstractTestEventListener<AiServiceCompletedEvent>
            implements AiServiceCompletedListener {}

    private static class TestInvocationErrorListener extends AbstractTestEventListener<AiServiceErrorEvent>
            implements AiServiceErrorListener {}

    private static class TestLLMResponseReceivedListener
            extends AbstractTestEventListener<AiServiceResponseReceivedEvent>
            implements AiServiceResponseReceivedListener {}

    private static class TestLLMRequestIssuedListener extends AbstractTestEventListener<AiServiceRequestIssuedEvent>
            implements AiServiceRequestIssuedListener {}

    private static class TestToolExecutedListener extends AbstractTestEventListener<ToolExecutedEvent>
            implements ToolExecutedEventListener {}

    private static class TestAiServiceInvocationSummaryListener
            extends AbstractTestEventListener<AiServiceInvocationSummaryEvent>
            implements AiServiceInvocationSummaryListener {}

    private static class IG implements InputGuardrail {}

    private static class OG implements OutputGuardrail {}

    private static class TIG implements ToolInputGuardrail {
        @Override
        public ToolInputGuardrailResult validate(ToolInputGuardrailRequest request) {
            return success();
        }
    }

    private static class TOG implements ToolOutputGuardrail {
        @Override
        public ToolOutputGuardrailResult validate(ToolOutputGuardrailRequest request) {
            return success();
        }
    }
}
