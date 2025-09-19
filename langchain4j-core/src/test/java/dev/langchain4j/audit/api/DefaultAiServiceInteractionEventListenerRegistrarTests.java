package dev.langchain4j.audit.api;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.audit.api.event.AiServiceInteractionCompletedEvent;
import dev.langchain4j.audit.api.event.AiServiceInteractionErrorEvent;
import dev.langchain4j.audit.api.event.AiServiceInteractionEvent;
import dev.langchain4j.audit.api.event.AiServiceInteractionStartedEvent;
import dev.langchain4j.audit.api.event.AiServiceInvocationContext;
import dev.langchain4j.audit.api.event.AiServiceResponseReceivedEvent;
import dev.langchain4j.audit.api.event.InputGuardrailExecutedEvent;
import dev.langchain4j.audit.api.event.OutputGuardrailExecutedEvent;
import dev.langchain4j.audit.api.event.ToolExecutedEvent;
import dev.langchain4j.audit.api.listener.AiServiceInteractionCompletedEventListener;
import dev.langchain4j.audit.api.listener.AiServiceInteractionEventListener;
import dev.langchain4j.audit.api.listener.AiServiceInteractionStartedEventListener;
import dev.langchain4j.audit.api.listener.AiServiceResponseReceivedEventListener;
import dev.langchain4j.audit.api.listener.InputGuardrailExecutedEventListener;
import dev.langchain4j.audit.api.listener.LLMInteractionErrorEventListener;
import dev.langchain4j.audit.api.listener.OutputGuardrailExecutedEventListener;
import dev.langchain4j.audit.api.listener.ToolExecutedEventListener;
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
import dev.langchain4j.model.chat.response.ChatResponse;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class DefaultAiServiceInteractionEventListenerRegistrarTests {
    private static final AiServiceInvocationContext DEFAULT_INTERACTION_SOURCE = AiServiceInvocationContext.builder()
            .interfaceName("SomeInterface")
            .methodName("someMethod")
            .methodArgument("one")
            .methodArgument("two")
            .memoryId("one")
            .build();

    private static final AiServiceResponseReceivedEvent LLM_RESPONSE_RECEIVED_EVENT =
            AiServiceResponseReceivedEvent.builder()
                    .invocationContext(DEFAULT_INTERACTION_SOURCE)
                    .response(ChatResponse.builder()
                            .aiMessage(AiMessage.from("Message!"))
                            .build())
                    .build();

    private static final AiServiceInteractionErrorEvent LLM_INTERACTION_ERROR_EVENT =
            AiServiceInteractionErrorEvent.builder()
                    .invocationContext(DEFAULT_INTERACTION_SOURCE)
                    .error(new RuntimeException("Some error"))
                    .build();

    private static final AiServiceInteractionCompletedEvent LLM_INTERACTION_COMPLETED_EVENT =
            AiServiceInteractionCompletedEvent.builder()
                    .invocationContext(DEFAULT_INTERACTION_SOURCE)
                    .build();

    private static final AiServiceInteractionStartedEvent LLM_INTERACTION_STARTED_EVENT =
            AiServiceInteractionStartedEvent.builder()
                    .invocationContext(DEFAULT_INTERACTION_SOURCE)
                    .userMessage(UserMessage.from("Hello, world!"))
                    .build();

    private static final OutputGuardrailExecutedEvent OUTPUT_GUARDRAIL_EXECUTED_EVENT =
            OutputGuardrailExecutedEvent.builder()
                    .invocationContext(DEFAULT_INTERACTION_SOURCE)
                    .guardrailClass(OG.class)
                    .request(OutputGuardrailRequest.builder()
                            .responseFromLLM(ChatResponse.builder()
                                    .aiMessage(AiMessage.from("Message!"))
                                    .build())
                            .requestParams(GuardrailRequestParams.builder()
                                    .userMessageTemplate("")
                                    .variables(Map.of())
                                    .invocationContext(DEFAULT_INTERACTION_SOURCE)
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
                    .build();

    private static final InputGuardrailExecutedEvent INPUT_GUARDRAIL_EXECUTED_EVENT =
            InputGuardrailExecutedEvent.builder()
                    .invocationContext(DEFAULT_INTERACTION_SOURCE)
                    .guardrailClass(IG.class)
                    .request(InputGuardrailRequest.builder()
                            .userMessage(UserMessage.from("Hello, world!"))
                            .commonParams(GuardrailRequestParams.builder()
                                    .userMessageTemplate("")
                                    .variables(Map.of())
                                    .invocationContext(DEFAULT_INTERACTION_SOURCE)
                                    .build())
                            .build())
                    .result(InputGuardrailResult.success())
                    .build();

    private static final ToolExecutedEvent TOOL_EXECUTED_EVENT = ToolExecutedEvent.builder()
            .invocationContext(DEFAULT_INTERACTION_SOURCE)
            .request(ToolExecutionRequest.builder().build())
            .result("Success!")
            .build();

    private static final List<AiServiceInteractionEvent> ALL_EVENTS = List.of(
            LLM_RESPONSE_RECEIVED_EVENT,
            LLM_INTERACTION_ERROR_EVENT,
            LLM_INTERACTION_COMPLETED_EVENT,
            LLM_INTERACTION_STARTED_EVENT,
            OUTPUT_GUARDRAIL_EXECUTED_EVENT,
            INPUT_GUARDRAIL_EXECUTED_EVENT,
            TOOL_EXECUTED_EVENT);

    // Create 2 instances of each listener
    private static final List<AbstractTestEventListener<?>> ALL_LISTENERS = IntStream.range(0, 2)
            .mapToObj(i -> List.of(
                    new TestInputGuardrailListener(),
                    new TestOutputGuardrailListener(),
                    new TestLLMInteractionStartedListener(),
                    new TestLLMInteractionCompletedListener(),
                    new TestLLMInteractionErrorListener(),
                    new TestLLMResponseReceivedListener(),
                    new TestToolExecutedListener()))
            .flatMap(List::stream)
            .toList();

    @Test
    void hasCorrectListeners() {
        var registrar = (DefaultAiServiceInteractionEventListenerRegistrar)
                assertThat(AiServiceInteractionEventListenerRegistrar.getInstance())
                        .isNotNull()
                        .isExactlyInstanceOf(DefaultAiServiceInteractionEventListenerRegistrar.class)
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
                        .extracting(AiServiceInteractionEvent::invocationContext)
                        .usingRecursiveComparison()
                        .isEqualTo(DEFAULT_INTERACTION_SOURCE)));

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
        assertThat(ALL_LISTENERS).isNotNull().hasSize(7 * 2).allSatisfy(l -> assertThat(l)
                .isNotNull()
                .satisfies(el -> assertThat(el.count()).isZero(), el -> assertThat(el.lastEvent())
                        .isNull()));
    }

    private abstract static class AbstractTestEventListener<T extends AiServiceInteractionEvent>
            implements AiServiceInteractionEventListener<T> {

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
            implements InputGuardrailExecutedEventListener {}

    private static class TestOutputGuardrailListener extends AbstractTestEventListener<OutputGuardrailExecutedEvent>
            implements OutputGuardrailExecutedEventListener {}

    private static class TestLLMInteractionStartedListener
            extends AbstractTestEventListener<AiServiceInteractionStartedEvent>
            implements AiServiceInteractionStartedEventListener {}

    private static class TestLLMInteractionCompletedListener
            extends AbstractTestEventListener<AiServiceInteractionCompletedEvent>
            implements AiServiceInteractionCompletedEventListener {}

    private static class TestLLMInteractionErrorListener
            extends AbstractTestEventListener<AiServiceInteractionErrorEvent>
            implements LLMInteractionErrorEventListener {}

    private static class TestLLMResponseReceivedListener
            extends AbstractTestEventListener<AiServiceResponseReceivedEvent>
            implements AiServiceResponseReceivedEventListener {}

    private static class TestToolExecutedListener extends AbstractTestEventListener<ToolExecutedEvent>
            implements ToolExecutedEventListener {}

    private static class IG implements InputGuardrail {}

    private static class OG implements OutputGuardrail {}
}
