package dev.langchain4j.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.fail;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.guardrail.InputGuardrail;
import dev.langchain4j.guardrail.InputGuardrailException;
import dev.langchain4j.guardrail.InputGuardrailResult;
import dev.langchain4j.guardrail.OutputGuardrail;
import dev.langchain4j.guardrail.OutputGuardrailException;
import dev.langchain4j.guardrail.OutputGuardrailResult;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.model.chat.mock.ChatModelMock;
import dev.langchain4j.model.chat.mock.StreamingChatModelMock;
import dev.langchain4j.observability.api.event.AiServiceCompletedEvent;
import dev.langchain4j.observability.api.event.AiServiceErrorEvent;
import dev.langchain4j.observability.api.event.AiServiceEvent;
import dev.langchain4j.observability.api.event.AiServiceResponseReceivedEvent;
import dev.langchain4j.observability.api.event.AiServiceStartedEvent;
import dev.langchain4j.observability.api.event.GuardrailExecutedEvent;
import dev.langchain4j.observability.api.event.InputGuardrailExecutedEvent;
import dev.langchain4j.observability.api.event.OutputGuardrailExecutedEvent;
import dev.langchain4j.observability.api.event.ToolExecutedEvent;
import dev.langchain4j.observability.api.listener.AiServiceCompletedListener;
import dev.langchain4j.observability.api.listener.AiServiceErrorListener;
import dev.langchain4j.observability.api.listener.AiServiceListener;
import dev.langchain4j.observability.api.listener.AiServiceResponseReceivedListener;
import dev.langchain4j.observability.api.listener.AiServiceStartedListener;
import dev.langchain4j.observability.api.listener.InputGuardrailExecutedListener;
import dev.langchain4j.observability.api.listener.OutputGuardrailExecutedListener;
import dev.langchain4j.observability.api.listener.ToolExecutedEventListener;
import dev.langchain4j.service.guardrail.InputGuardrails;
import dev.langchain4j.service.guardrail.OutputGuardrails;
import dev.langchain4j.service.memory.ChatMemoryService;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class AiServicesObservabilityTests {

    private static final String DEFAULT_EXPECTED_RESPONSE = "Hello how are you today?";
    private static final String TOOL_USER_MESSAGE =
            "What is the square root of 485906798473894056 in scientific notation?";
    private static final String TOOL_EXPECTED_RESPONSE =
            "The square root of 485,906,798,473,894,056 in scientific notation is approximately 6.97070153193991E8.";

    Map<Class<? extends AiServiceEvent>, MyListener<? extends AiServiceEvent>> listeners = createListeners();

    private void runScenario(
            Supplier<Assistant> assistantCreatorWithoutListeners,
            Supplier<Assistant> assistantCreatorWithListeners,
            Consumer<Assistant> chatAssertion,
            String expectedMethodName,
            boolean hasTools,
            List<Class<? extends AiServiceEvent>> noEventsReceivedClasses,
            String expectedUserMessage,
            List<Class<? extends AiServiceEvent>> expectedEventsReceivedClasses) {

        // Invoke the operation without registered listeners
        // No listeners should be fired
        chatAssertion.accept(assistantCreatorWithoutListeners.get());
        assertNoEventsReceived(7, listeners.values());

        // Let's invoke the operation a few times with the registered listeners
        IntStream.range(0, 5).forEach(i -> chatAssertion.accept(assistantCreatorWithListeners.get()));

        assertNoEventsReceived(
                noEventsReceivedClasses.size(),
                noEventsReceivedClasses.stream().map(listeners::get).toList());

        assertEventsReceived(
                hasTools,
                expectedEventsReceivedClasses.size(),
                expectedUserMessage,
                expectedMethodName,
                expectedEventsReceivedClasses.stream().map(listeners::get).toList());

        // No additional events should fire when invoking the operation again
        chatAssertion.accept(assistantCreatorWithoutListeners.get());
        assertEventsReceived(
                hasTools,
                expectedEventsReceivedClasses.size(),
                expectedUserMessage,
                expectedMethodName,
                expectedEventsReceivedClasses.stream().map(listeners::get).toList());
    }

    @Test
    void failureStreamingChat() {
        runScenario(
                () -> Assistant.createFailingService(true),
                () -> Assistant.createFailingService(true, listeners.values()),
                assistant -> {
                    var latch = new CountDownLatch(1);

                    try {
                        assistant
                                .streamingChat("Hello!")
                                .onError(t -> {
                                    try {
                                        assertThat(t)
                                                .isNotNull()
                                                .isInstanceOf(RuntimeException.class)
                                                .hasMessage("LLM invocation failed");
                                    } finally {
                                        latch.countDown();
                                    }
                                })
                                .onCompleteResponse(r -> {
                                    try {
                                        fail("onCompleteResponse should not be called");
                                    } finally {
                                        latch.countDown();
                                    }
                                })
                                .onPartialResponse(r -> fail("onPartialResponse should not be called"))
                                .onToolExecuted(t -> fail("onToolExecuted should not be called"))
                                .start();
                    } finally {
                        try {
                            latch.await(1, TimeUnit.MINUTES);
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                    }
                },
                "streamingChat",
                false,
                List.of(
                        AiServiceCompletedEvent.class,
                        InputGuardrailExecutedEvent.class,
                        OutputGuardrailExecutedEvent.class,
                        AiServiceResponseReceivedEvent.class,
                        ToolExecutedEvent.class),
                "Hello!",
                List.of(AiServiceStartedEvent.class, AiServiceErrorEvent.class));
    }

    @Test
    void failureChat() {
        runScenario(
                () -> Assistant.createFailingService(false),
                () -> Assistant.createFailingService(false, listeners.values()),
                assistant ->
                        assertThatExceptionOfType(RuntimeException.class).isThrownBy(() -> assistant.chat("Hello!")),
                "chat",
                false,
                List.of(
                        AiServiceCompletedEvent.class,
                        InputGuardrailExecutedEvent.class,
                        OutputGuardrailExecutedEvent.class,
                        AiServiceResponseReceivedEvent.class,
                        ToolExecutedEvent.class),
                "Hello!",
                List.of(AiServiceStartedEvent.class, AiServiceErrorEvent.class));
    }

    @Test
    void successfulStreamingChatNoTools() {
        runScenario(
                () -> Assistant.create(false, true),
                () -> Assistant.create(false, true, listeners.values()),
                assistant -> {
                    var latch = new CountDownLatch(1);

                    try {
                        assistant
                                .streamingChat("Hello!")
                                .onPartialResponse(r -> assertThat(r).isNotNull())
                                .onCompleteResponse(response -> {
                                    try {
                                        assertThat(response)
                                                .isNotNull()
                                                .extracting(r -> r.aiMessage().text())
                                                .isEqualTo(DEFAULT_EXPECTED_RESPONSE);
                                    } finally {
                                        latch.countDown();
                                    }
                                })
                                .onError(t -> {
                                    try {
                                        fail("onError should not be called");
                                    } finally {
                                        latch.countDown();
                                    }
                                })
                                .onToolExecuted(t -> fail("onToolExecuted should not be called"))
                                .start();
                    } finally {
                        try {
                            latch.await(1, TimeUnit.MINUTES);
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                    }
                },
                "streamingChat",
                false,
                List.of(
                        AiServiceErrorEvent.class,
                        InputGuardrailExecutedEvent.class,
                        OutputGuardrailExecutedEvent.class,
                        ToolExecutedEvent.class),
                "Hello!",
                List.of(
                        AiServiceStartedEvent.class,
                        AiServiceCompletedEvent.class,
                        AiServiceResponseReceivedEvent.class));
    }

    @Test
    void successfulChatNoTools() {
        runScenario(
                () -> Assistant.create(false, false),
                () -> Assistant.create(false, false, listeners.values()),
                assistant -> assertThat(assistant.chat("Hello!")).isEqualTo(DEFAULT_EXPECTED_RESPONSE),
                "chat",
                false,
                List.of(
                        AiServiceErrorEvent.class,
                        InputGuardrailExecutedEvent.class,
                        OutputGuardrailExecutedEvent.class,
                        ToolExecutedEvent.class),
                "Hello!",
                List.of(
                        AiServiceStartedEvent.class,
                        AiServiceCompletedEvent.class,
                        AiServiceResponseReceivedEvent.class));
    }

    @Test
    void successfulChatWithTools() {
        runScenario(
                () -> Assistant.create(true, false),
                () -> Assistant.create(true, false, listeners.values()),
                assistant -> assertThat(assistant.chat(TOOL_USER_MESSAGE)).isEqualTo(TOOL_EXPECTED_RESPONSE),
                "chat",
                true,
                List.of(
                        AiServiceErrorEvent.class,
                        InputGuardrailExecutedEvent.class,
                        OutputGuardrailExecutedEvent.class),
                TOOL_USER_MESSAGE,
                List.of(
                        AiServiceStartedEvent.class,
                        AiServiceCompletedEvent.class,
                        AiServiceResponseReceivedEvent.class,
                        ToolExecutedEvent.class));
    }

    @Test
    void successfulStreamingChatWithTools() {
        runScenario(
                () -> Assistant.create(true, true),
                () -> Assistant.create(true, true, listeners.values()),
                assistant -> {
                    var latch = new CountDownLatch(1);

                    try {
                        assistant
                                .streamingChat(TOOL_USER_MESSAGE)
                                .onPartialResponse(r -> assertThat(r).isNotNull())
                                .onToolExecuted(t -> assertThat(t)
                                        .isNotNull()
                                        .extracting(te -> te.request().name())
                                        .isEqualTo("squareRoot"))
                                .onCompleteResponse(response -> {
                                    try {
                                        assertThat(response)
                                                .isNotNull()
                                                .extracting(r -> r.aiMessage().text())
                                                .isEqualTo(TOOL_EXPECTED_RESPONSE);
                                    } finally {
                                        latch.countDown();
                                    }
                                })
                                .onError(t -> {
                                    try {
                                        fail("onError should not be called");
                                    } finally {
                                        latch.countDown();
                                    }
                                })
                                .start();
                    } finally {
                        try {
                            latch.await(1, TimeUnit.MINUTES);
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                    }
                },
                "streamingChat",
                true,
                List.of(
                        AiServiceErrorEvent.class,
                        InputGuardrailExecutedEvent.class,
                        OutputGuardrailExecutedEvent.class),
                TOOL_USER_MESSAGE,
                List.of(
                        AiServiceStartedEvent.class,
                        AiServiceCompletedEvent.class,
                        AiServiceResponseReceivedEvent.class,
                        ToolExecutedEvent.class));
    }

    @Test
    void streamingWithInputGuardrails() {
        runScenario(
                () -> Assistant.create(false, true),
                () -> Assistant.create(false, true, listeners.values()),
                assistant -> assertThatExceptionOfType(InputGuardrailException.class)
                        .isThrownBy(() -> assistant
                                .streamingChatWithInputGuardrails("Hello!")
                                .start())
                        .withMessage(
                                "The guardrail %s failed with this message: User message is not valid",
                                FailureInputGuardrail.class.getName()),
                "streamingChatWithInputGuardrails",
                false,
                List.of(
                        AiServiceCompletedEvent.class,
                        OutputGuardrailExecutedEvent.class,
                        ToolExecutedEvent.class,
                        AiServiceResponseReceivedEvent.class),
                "Hello!",
                List.of(AiServiceStartedEvent.class, AiServiceErrorEvent.class, InputGuardrailExecutedEvent.class));
    }

    @Test
    void withInputGuardrails() {
        runScenario(
                () -> Assistant.create(false, false),
                () -> Assistant.create(false, false, listeners.values()),
                assistant -> assertThatExceptionOfType(InputGuardrailException.class)
                        .isThrownBy(() -> assistant.chatWithInputGuardrails("Hello!"))
                        .withMessage(
                                "The guardrail %s failed with this message: User message is not valid",
                                FailureInputGuardrail.class.getName()),
                "chatWithInputGuardrails",
                false,
                List.of(
                        AiServiceCompletedEvent.class,
                        OutputGuardrailExecutedEvent.class,
                        ToolExecutedEvent.class,
                        AiServiceResponseReceivedEvent.class),
                "Hello!",
                List.of(AiServiceStartedEvent.class, AiServiceErrorEvent.class, InputGuardrailExecutedEvent.class));
    }

    @Test
    void streamingWithOutputGuardrails() {
        runScenario(
                () -> Assistant.create(false, true),
                () -> Assistant.create(false, true, listeners.values()),
                assistant -> {
                    var latch = new CountDownLatch(1);

                    try {
                        assistant
                                .streamingChatWithOutputGuardrails("Hello!")
                                .onError(t -> {
                                    try {
                                        assertThat(t)
                                                .isNotNull()
                                                .isInstanceOf(OutputGuardrailException.class)
                                                .hasMessage(
                                                        "The guardrail %s failed with this message: LLM response is not valid",
                                                        FailureOutputGuardrail.class.getName());
                                    } finally {
                                        latch.countDown();
                                    }
                                })
                                .onCompleteResponse(r -> {
                                    try {
                                        fail("onCompleteResponse should not be called");
                                    } finally {
                                        latch.countDown();
                                    }
                                })
                                .onPartialResponse(r -> fail("onPartialResponse should not be called"))
                                .onToolExecuted(t -> fail("onToolExecuted should not be called"))
                                .start();
                    } finally {
                        try {
                            latch.await(1, TimeUnit.MINUTES);
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                    }
                },
                "streamingChatWithOutputGuardrails",
                false,
                List.of(AiServiceCompletedEvent.class, InputGuardrailExecutedEvent.class, ToolExecutedEvent.class),
                "Hello!",
                List.of(
                        AiServiceStartedEvent.class,
                        AiServiceErrorEvent.class,
                        OutputGuardrailExecutedEvent.class,
                        AiServiceResponseReceivedEvent.class));
    }

    @Test
    void withOutputGuardrails() {
        runScenario(
                () -> Assistant.create(false, false),
                () -> Assistant.create(false, false, listeners.values()),
                assistant -> assertThatExceptionOfType(OutputGuardrailException.class)
                        .isThrownBy(() -> assistant.chatWithOutputGuardrails("Hello!"))
                        .withMessage(
                                "The guardrail %s failed with this message: LLM response is not valid",
                                FailureOutputGuardrail.class.getName()),
                "chatWithOutputGuardrails",
                false,
                List.of(AiServiceCompletedEvent.class, InputGuardrailExecutedEvent.class, ToolExecutedEvent.class),
                "Hello!",
                List.of(
                        AiServiceStartedEvent.class,
                        AiServiceErrorEvent.class,
                        OutputGuardrailExecutedEvent.class,
                        AiServiceResponseReceivedEvent.class));
    }

    private static void assertNoEventsReceived(
            int expectedSize, Collection<? extends MyListener<? extends AiServiceEvent>> listeners) {
        assertThat(listeners).isNotNull().hasSize(expectedSize).allSatisfy(l -> assertThat(l)
                .isNotNull()
                .extracting(MyListener::count, MyListener::event)
                .containsExactly(0, null));
    }

    private static void assertEventsReceived(
            boolean hasTools,
            int expectedSize,
            String expectedUserMessage,
            String expectedMethodName,
            Collection<? extends MyListener<? extends AiServiceEvent>> listeners) {

        // All the events have the correct number of invocations & non-null events
        assertThat(listeners).isNotNull().hasSize(expectedSize).allSatisfy(l -> {
            assertThat(l)
                    .isNotNull()
                    .extracting(MyListener::count)
                    .isEqualTo(
                            (GuardrailExecutedEvent.class.isAssignableFrom(l.getEventClass())
                                            || (hasTools
                                                    && AiServiceResponseReceivedEvent.class.isAssignableFrom(
                                                            l.getEventClass())))
                                    ? 10
                                    : 5);

            assertThat(l).isNotNull().extracting(MyListener::event).isNotNull();
        });

        var firstInvocationContext = listeners.stream()
                .map(MyListener::event)
                .map(AiServiceEvent::invocationContext)
                .findFirst();

        var ic = assertThat(firstInvocationContext).get().actual();

        // Verify that all the listeners have the same invocationContext()
        assertThat(listeners)
                .extracting(l -> l.event().invocationContext())
                .usingRecursiveFieldByFieldElementComparator()
                .containsOnly(ic);

        // And because all the invocationContext() is the same, verify that it has the correct information
        assertThat(ic)
                .isNotNull()
                .extracting(
                        InvocationContext::interfaceName,
                        InvocationContext::methodName,
                        InvocationContext::methodArguments,
                        InvocationContext::chatMemoryId)
                .containsExactly(
                        Assistant.class.getName(),
                        expectedMethodName,
                        List.of(expectedUserMessage),
                        ChatMemoryService.DEFAULT);

        assertThat(ic.invocationId()).isNotNull();
        assertThat(ic.timestamp()).isNotNull();
    }

    private static Map<Class<? extends AiServiceEvent>, MyListener<? extends AiServiceEvent>> createListeners() {

        return Stream.of(
                        new MyInputGuardrailExecutedListener(),
                        new MyAiServiceCompletedListener(),
                        new MyAiServiceErrorListener(),
                        new MyAiServiceStartedListener(),
                        new MyAiServiceResponseReceivedListener(),
                        new MyOutputGuardrailExecutedListener(),
                        new MyToolExecutedListener())
                .collect(Collectors.toMap(AiServiceListener::getEventClass, Function.identity()));
    }

    @SystemMessage("You are a chat bot that answers questions")
    interface Assistant {
        String chat(String message);

        TokenStream streamingChat(String message);

        @InputGuardrails({SuccessInputGuardrail.class, FailureInputGuardrail.class})
        String chatWithInputGuardrails(String message);

        @InputGuardrails({SuccessInputGuardrail.class, FailureInputGuardrail.class})
        TokenStream streamingChatWithInputGuardrails(String message);

        @OutputGuardrails({SuccessOutputGuardrail.class, FailureOutputGuardrail.class})
        String chatWithOutputGuardrails(String message);

        @OutputGuardrails({SuccessOutputGuardrail.class, FailureOutputGuardrail.class})
        TokenStream streamingChatWithOutputGuardrails(String message);

        static Assistant create(boolean shouldHaveToolAccess, boolean streaming) {
            return create(shouldHaveToolAccess, streaming, List.of());
        }

        static Assistant create(
                boolean shouldHaveToolAccess,
                boolean streaming,
                Collection<? extends AiServiceListener<? extends AiServiceEvent>> listeners) {
            var builder = AiServices.builder(Assistant.class).registerListeners(listeners);

            var toolRequestMessage = AiMessage.from(ToolExecutionRequest.builder()
                    .name("squareRoot")
                    .arguments("{\n  \"arg0\": 485906798473894056\n}")
                    .build());
            var toolResponseMessage = AiMessage.from(TOOL_EXPECTED_RESPONSE);

            if (shouldHaveToolAccess) {
                if (streaming) {
                    builder.executeToolsConcurrently()
                            .streamingChatModel(
                                    StreamingChatModelMock.thatAlwaysStreams(toolRequestMessage, toolResponseMessage))
                            .tools(new Calculator());
                } else {
                    builder.chatModel(ChatModelMock.thatAlwaysResponds(toolRequestMessage, toolResponseMessage))
                            .tools(new Calculator());
                }
            } else {
                if (streaming) {
                    builder.executeToolsConcurrently()
                            .streamingChatModel(StreamingChatModelMock.thatAlwaysStreams(
                                    AiMessage.from(DEFAULT_EXPECTED_RESPONSE)));
                } else {
                    builder.chatModel(ChatModelMock.thatAlwaysResponds(DEFAULT_EXPECTED_RESPONSE));
                }
            }

            return builder.build();
        }

        static Assistant createFailingService(boolean streaming) {
            return createFailingService(streaming, List.of());
        }

        static Assistant createFailingService(
                boolean streaming, Collection<? extends AiServiceListener<? extends AiServiceEvent>> listeners) {
            var builder = AiServices.builder(Assistant.class).registerListeners(listeners);

            return streaming
                    ? builder.streamingChatModel(StreamingChatModelMock.thatAlwaysThrowsExceptionWithMessage(
                                    "LLM invocation failed"))
                            .build()
                    : builder.chatModel(ChatModelMock.thatAlwaysThrowsExceptionWithMessage("LLM invocation failed"))
                            .build();
        }
    }

    public static class Calculator {
        @Tool("calculates the square root of the provided number")
        public double squareRoot(double number) {
            return Math.sqrt(number);
        }
    }

    public static class SuccessInputGuardrail implements InputGuardrail {
        @Override
        public InputGuardrailResult validate(UserMessage userMessage) {
            return successWith("Success!!");
        }
    }

    public static class FailureInputGuardrail implements InputGuardrail {
        @Override
        public InputGuardrailResult validate(UserMessage userMessage) {
            return failure("User message is not valid");
        }
    }

    public static class SuccessOutputGuardrail implements OutputGuardrail {
        @Override
        public OutputGuardrailResult validate(AiMessage responseFromLLM) {
            return successWith("Success!!");
        }
    }

    public static class FailureOutputGuardrail implements OutputGuardrail {
        @Override
        public OutputGuardrailResult validate(AiMessage responseFromLLM) {
            return failure("LLM response is not valid");
        }
    }

    public abstract static class MyListener<T extends AiServiceEvent> implements AiServiceListener<T> {
        private final AtomicInteger count = new AtomicInteger();
        private T event;

        @Override
        public void onEvent(T event) {
            this.event = event;
            increment();
        }

        public T event() {
            return event;
        }

        protected void increment() {
            count.incrementAndGet();
        }

        public int count() {
            return count.get();
        }
    }

    public static class MyToolExecutedListener extends MyListener<ToolExecutedEvent>
            implements ToolExecutedEventListener {}

    public static class MyOutputGuardrailExecutedListener extends MyListener<OutputGuardrailExecutedEvent>
            implements OutputGuardrailExecutedListener {}

    public static class MyAiServiceResponseReceivedListener extends MyListener<AiServiceResponseReceivedEvent>
            implements AiServiceResponseReceivedListener {}

    public static class MyAiServiceStartedListener extends MyListener<AiServiceStartedEvent>
            implements AiServiceStartedListener {}

    public static class MyAiServiceErrorListener extends MyListener<AiServiceErrorEvent>
            implements AiServiceErrorListener {}

    public static class MyAiServiceCompletedListener extends MyListener<AiServiceCompletedEvent>
            implements AiServiceCompletedListener {}

    public static class MyInputGuardrailExecutedListener extends MyListener<InputGuardrailExecutedEvent>
            implements InputGuardrailExecutedListener {}
}
