package dev.langchain4j.observability.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNoException;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.guardrail.ChatExecutor;
import dev.langchain4j.guardrail.GuardrailRequestParams;
import dev.langchain4j.guardrail.InputGuardrail;
import dev.langchain4j.guardrail.InputGuardrailRequest;
import dev.langchain4j.guardrail.InputGuardrailResult;
import dev.langchain4j.guardrail.OutputGuardrail;
import dev.langchain4j.guardrail.OutputGuardrailRequest;
import dev.langchain4j.guardrail.OutputGuardrailResult;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.observability.api.event.AiServiceCompletedEvent;
import dev.langchain4j.observability.api.event.AiServiceErrorEvent;
import dev.langchain4j.observability.api.event.AiServiceEvent;
import dev.langchain4j.observability.api.event.AiServiceRequestIssuedEvent;
import dev.langchain4j.observability.api.event.AiServiceResponseReceivedEvent;
import dev.langchain4j.observability.api.event.AiServiceStartedEvent;
import dev.langchain4j.observability.api.event.InputGuardrailExecutedEvent;
import dev.langchain4j.observability.api.event.OutputGuardrailExecutedEvent;
import dev.langchain4j.observability.api.event.ToolExecutedEvent;
import dev.langchain4j.observability.api.listener.AiServiceCompletedListener;
import dev.langchain4j.observability.api.listener.AiServiceErrorListener;
import dev.langchain4j.observability.api.listener.AiServiceListener;
import dev.langchain4j.observability.api.listener.AiServiceRequestIssuedListener;
import dev.langchain4j.observability.api.listener.AiServiceResponseReceivedListener;
import dev.langchain4j.observability.api.listener.AiServiceStartedListener;
import dev.langchain4j.observability.api.listener.InputGuardrailExecutedListener;
import dev.langchain4j.observability.api.listener.OutputGuardrailExecutedListener;
import dev.langchain4j.observability.api.listener.ToolExecutedEventListener;
import org.junit.jupiter.api.Test;

class DefaultAiServiceListenerRegistrarTests {

    private static final AiServiceListenerRegistrar REGISTRAR = AiServiceListenerRegistrar.newInstance();

    private static final InvocationContext DEFAULT_INVOCATION_CONTEXT = InvocationContext.builder()
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
            TOOL_EXECUTED_EVENT);

    // Create 2 instances of each listener
    private static final List<AbstractTestEventListener<?>> ALL_LISTENERS = IntStream.range(0, 2)
            .mapToObj(i -> List.of(
                    new TestInputGuardrailListener(),
                    new TestOutputGuardrailListener(),
                    new TestInvocationStartedListener(),
                    new TestInvocationCompletedListener(),
                    new TestInvocationErrorListener(),
                    new TestLLMResponseReceivedListener(),
                    new TestLLMRequestIssuedListener(),
                    new TestToolExecutedListener()))
            .flatMap(List::stream)
            .toList();

    @Test
    void registrarEatsThrownExceptionsDuringFiring() {
        // From https://github.com/langchain4j/langchain4j/issues/4499
        var registrar = AiServiceListenerRegistrar.newInstance();

        registrar.register((AiServiceStartedListener) event -> {
            throw new RuntimeException("Some error");
        });

        assertThatNoException()
                .isThrownBy(() -> registrar.fireEvent(INVOCATION_STARTED_EVENT));
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
        assertThat(ALL_LISTENERS).allSatisfy(l -> assertThat(l)
                .isNotNull()
                .satisfies(el -> assertThat(el.count()).isOne(), el -> assertThat(el.lastEvent())
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

    private static void assertListenersNotExecuted() {
        assertThat(ALL_LISTENERS).isNotNull().hasSize(8 * 2).allSatisfy(l -> assertThat(l)
                .isNotNull()
                .satisfies(el -> assertThat(el.count()).isZero(), el -> assertThat(el.lastEvent())
                        .isNull()));
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

    private static class IG implements InputGuardrail {}

    private static class OG implements OutputGuardrail {}
}
