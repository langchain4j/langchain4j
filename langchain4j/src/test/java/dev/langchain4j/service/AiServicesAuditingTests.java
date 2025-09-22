package dev.langchain4j.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.fail;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.audit.api.AiServiceInvocationEventListenerRegistrar;
import dev.langchain4j.audit.api.event.AiServiceInvocationCompletedEvent;
import dev.langchain4j.audit.api.event.AiServiceInvocationContext;
import dev.langchain4j.audit.api.event.AiServiceInvocationErrorEvent;
import dev.langchain4j.audit.api.event.AiServiceInvocationEvent;
import dev.langchain4j.audit.api.event.AiServiceInvocationStartedEvent;
import dev.langchain4j.audit.api.event.AiServiceResponseReceivedEvent;
import dev.langchain4j.audit.api.event.GuardrailExecutedEvent;
import dev.langchain4j.audit.api.event.InputGuardrailExecutedEvent;
import dev.langchain4j.audit.api.event.OutputGuardrailExecutedEvent;
import dev.langchain4j.audit.api.event.ToolExecutedEvent;
import dev.langchain4j.audit.api.listener.AiServiceInvocationCompletedEventListener;
import dev.langchain4j.audit.api.listener.AiServiceInvocationErrorEventListener;
import dev.langchain4j.audit.api.listener.AiServiceInvocationEventListener;
import dev.langchain4j.audit.api.listener.AiServiceInvocationStartedEventListener;
import dev.langchain4j.audit.api.listener.AiServiceResponseReceivedEventListener;
import dev.langchain4j.audit.api.listener.InputGuardrailExecutedEventListener;
import dev.langchain4j.audit.api.listener.OutputGuardrailExecutedEventListener;
import dev.langchain4j.audit.api.listener.ToolExecutedEventListener;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.guardrail.InputGuardrail;
import dev.langchain4j.guardrail.InputGuardrailException;
import dev.langchain4j.guardrail.InputGuardrailResult;
import dev.langchain4j.guardrail.OutputGuardrail;
import dev.langchain4j.guardrail.OutputGuardrailException;
import dev.langchain4j.guardrail.OutputGuardrailResult;
import dev.langchain4j.model.chat.mock.ChatModelMock;
import dev.langchain4j.model.chat.mock.StreamingChatModelMock;
import dev.langchain4j.service.guardrail.InputGuardrails;
import dev.langchain4j.service.guardrail.OutputGuardrails;
import org.junit.jupiter.api.Test;

class AiServicesAuditingTests {
    private static final String DEFAULT_EXPECTED_RESPONSE = "Hello how are you today?";
    private static final String TOOL_USER_MESSAGE =
            "What is the square root of 485906798473894056 in scientific notation?";
    private static final String TOOL_EXPECTED_RESPONSE =
            "The square root of 485,906,798,473,894,056 in scientific notation is approximately 6.97070153193991E8.";

    Map<Class<? extends AiServiceInvocationEvent>, MyEventListener<? extends AiServiceInvocationEvent>> listeners =
            createListeners();

    private void runScenario(
            Supplier<Assistant> assistantCreator,
            Consumer<Assistant> chatAssertion,
            String expectedMethodName,
            boolean hasTools,
            List<Class<? extends AiServiceInvocationEvent>> noEventsReceivedClasses,
            String expectedUserMessage,
            List<Class<? extends AiServiceInvocationEvent>> expectedEventsReceivedClasses) {

        // Invoke the operation prior to registering the listeners
        // Nothing should happen
        chatAssertion.accept(assistantCreator.get());
        assertNoEventsReceived(7, listeners.values());

        // Now register the events
        registerAllListeners();

        // Let's invoke the operation a few times
        IntStream.range(0, 5).forEach(i -> chatAssertion.accept(assistantCreator.get()));

        assertNoEventsReceived(
                noEventsReceivedClasses.size(),
                noEventsReceivedClasses.stream().map(listeners::get).toList());

        assertEventsReceived(
                hasTools,
                expectedEventsReceivedClasses.size(),
                expectedUserMessage,
                expectedMethodName,
                expectedEventsReceivedClasses.stream().map(listeners::get).toList());

        // Now unregister
        unregisterAllListeners();

        // Nothing should happen when invoking the operation again
        chatAssertion.accept(assistantCreator.get());
        assertNoEventsReceived(7, listeners.values());
    }

    @Test
    void failureStreamingChat() {
        runScenario(
                () -> Assistant.createFailingService(true),
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
                        AiServiceInvocationCompletedEvent.class,
                        InputGuardrailExecutedEvent.class,
                        OutputGuardrailExecutedEvent.class,
                        AiServiceResponseReceivedEvent.class,
                        ToolExecutedEvent.class),
                "Hello!",
                List.of(AiServiceInvocationStartedEvent.class, AiServiceInvocationErrorEvent.class));
    }

    @Test
    void failureChat() {
        runScenario(
                () -> Assistant.createFailingService(false),
                assistant ->
                        assertThatExceptionOfType(RuntimeException.class).isThrownBy(() -> assistant.chat("Hello!")),
                "chat",
                false,
                List.of(
                        AiServiceInvocationCompletedEvent.class,
                        InputGuardrailExecutedEvent.class,
                        OutputGuardrailExecutedEvent.class,
                        AiServiceResponseReceivedEvent.class,
                        ToolExecutedEvent.class),
                "Hello!",
                List.of(AiServiceInvocationStartedEvent.class, AiServiceInvocationErrorEvent.class));
    }

    @Test
    void successfulStreamingChatNoTools() {
        runScenario(
                () -> Assistant.create(false, true),
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
                        AiServiceInvocationErrorEvent.class,
                        InputGuardrailExecutedEvent.class,
                        OutputGuardrailExecutedEvent.class,
                        ToolExecutedEvent.class),
                "Hello!",
                List.of(
                        AiServiceInvocationStartedEvent.class,
                        AiServiceInvocationCompletedEvent.class,
                        AiServiceResponseReceivedEvent.class));
    }

    @Test
    void successfulChatNoTools() {
        runScenario(
                () -> Assistant.create(false, false),
                assistant -> assertThat(assistant.chat("Hello!")).isEqualTo(DEFAULT_EXPECTED_RESPONSE),
                "chat",
                false,
                List.of(
                        AiServiceInvocationErrorEvent.class,
                        InputGuardrailExecutedEvent.class,
                        OutputGuardrailExecutedEvent.class,
                        ToolExecutedEvent.class),
                "Hello!",
                List.of(
                        AiServiceInvocationStartedEvent.class,
                        AiServiceInvocationCompletedEvent.class,
                        AiServiceResponseReceivedEvent.class));
    }

    @Test
    void successfulChatWithTools() {
        runScenario(
                () -> Assistant.create(true, false),
                assistant -> assertThat(assistant.chat(TOOL_USER_MESSAGE)).isEqualTo(TOOL_EXPECTED_RESPONSE),
                "chat",
                true,
                List.of(
                        AiServiceInvocationErrorEvent.class,
                        InputGuardrailExecutedEvent.class,
                        OutputGuardrailExecutedEvent.class),
                TOOL_USER_MESSAGE,
                List.of(
                        AiServiceInvocationStartedEvent.class,
                        AiServiceInvocationCompletedEvent.class,
                        AiServiceResponseReceivedEvent.class,
                        ToolExecutedEvent.class));
    }

    @Test
    void successfulStreamingChatWithTools() {
        runScenario(
                () -> Assistant.create(true, true),
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
                        AiServiceInvocationErrorEvent.class,
                        InputGuardrailExecutedEvent.class,
                        OutputGuardrailExecutedEvent.class),
                TOOL_USER_MESSAGE,
                List.of(
                        AiServiceInvocationStartedEvent.class,
                        AiServiceInvocationCompletedEvent.class,
                        AiServiceResponseReceivedEvent.class,
                        ToolExecutedEvent.class));
    }

    @Test
    void auditingStreamingWithInputGuardrails() {
        runScenario(
                () -> Assistant.create(false, true),
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
                        AiServiceInvocationCompletedEvent.class,
                        OutputGuardrailExecutedEvent.class,
                        ToolExecutedEvent.class,
                        AiServiceResponseReceivedEvent.class),
                "Hello!",
                List.of(
                        AiServiceInvocationStartedEvent.class,
                        AiServiceInvocationErrorEvent.class,
                        InputGuardrailExecutedEvent.class));
    }

    @Test
    void auditingWithInputGuardrails() {
        runScenario(
                () -> Assistant.create(false, false),
                assistant -> assertThatExceptionOfType(InputGuardrailException.class)
                        .isThrownBy(() -> assistant.chatWithInputGuardrails("Hello!"))
                        .withMessage(
                                "The guardrail %s failed with this message: User message is not valid",
                                FailureInputGuardrail.class.getName()),
                "chatWithInputGuardrails",
                false,
                List.of(
                        AiServiceInvocationCompletedEvent.class,
                        OutputGuardrailExecutedEvent.class,
                        ToolExecutedEvent.class,
                        AiServiceResponseReceivedEvent.class),
                "Hello!",
                List.of(
                        AiServiceInvocationStartedEvent.class,
                        AiServiceInvocationErrorEvent.class,
                        InputGuardrailExecutedEvent.class));
    }

    @Test
    void auditingStreamingWithOutputGuardrails() {
        runScenario(
                () -> Assistant.create(false, true),
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
                List.of(
                        AiServiceInvocationCompletedEvent.class,
                        InputGuardrailExecutedEvent.class,
                        ToolExecutedEvent.class),
                "Hello!",
                List.of(
                        AiServiceInvocationStartedEvent.class,
                        AiServiceInvocationErrorEvent.class,
                        OutputGuardrailExecutedEvent.class,
                        AiServiceResponseReceivedEvent.class));
    }

    @Test
    void auditingWithOutputGuardrails() {
        runScenario(
                () -> Assistant.create(false, false),
                assistant -> assertThatExceptionOfType(OutputGuardrailException.class)
                        .isThrownBy(() -> assistant.chatWithOutputGuardrails("Hello!"))
                        .withMessage(
                                "The guardrail %s failed with this message: LLM response is not valid",
                                FailureOutputGuardrail.class.getName()),
                "chatWithOutputGuardrails",
                false,
                List.of(
                        AiServiceInvocationCompletedEvent.class,
                        InputGuardrailExecutedEvent.class,
                        ToolExecutedEvent.class),
                "Hello!",
                List.of(
                        AiServiceInvocationStartedEvent.class,
                        AiServiceInvocationErrorEvent.class,
                        OutputGuardrailExecutedEvent.class,
                        AiServiceResponseReceivedEvent.class));
    }

    private void registerAllListeners() {
        listeners.values().forEach(AiServiceInvocationEventListenerRegistrar.getInstance()::register);
    }

    private void unregisterAllListeners() {
        listeners.values().forEach(listener -> {
            AiServiceInvocationEventListenerRegistrar.getInstance().unregister(listener);
            listener.reset();
        });
    }

    private static void assertNoEventsReceived(
            int expectedSize, Collection<? extends MyEventListener<? extends AiServiceInvocationEvent>> listeners) {
        assertThat(listeners).isNotNull().hasSize(expectedSize).allSatisfy(l -> assertThat(l)
                .isNotNull()
                .extracting(MyEventListener::count, MyEventListener::event)
                .containsExactly(0, null));
    }

    private static void assertEventsReceived(
            boolean hasTools,
            int expectedSize,
            String expectedUserMessage,
            String expectedMethodName,
            Collection<? extends MyEventListener<? extends AiServiceInvocationEvent>> listeners) {

        // All the events have the correct number of invocations & non-null events
        assertThat(listeners).isNotNull().hasSize(expectedSize).allSatisfy(l -> {
            assertThat(l)
                    .isNotNull()
                    .extracting(MyEventListener::count)
                    .isEqualTo(
                            (GuardrailExecutedEvent.class.isAssignableFrom(l.getEventClass())
                                            || (hasTools
                                                    && AiServiceResponseReceivedEvent.class.isAssignableFrom(
                                                            l.getEventClass())))
                                    ? 10
                                    : 5);

            assertThat(l).isNotNull().extracting(MyEventListener::event).isNotNull();
        });

        var firstInteractionSource = listeners.stream()
                .map(MyEventListener::event)
                .map(AiServiceInvocationEvent::invocationContext)
                .findFirst();

        var is = assertThat(firstInteractionSource).get().actual();

        // Verify that all the listeners have the same invocationContext()
        assertThat(listeners)
                .extracting(l -> l.event().invocationContext())
                .usingRecursiveFieldByFieldElementComparator()
                .containsOnly(is);

        // And because all the invocationContext() is the same, verify that it has the correct information
        assertThat(is)
                .isNotNull()
                .extracting(
                        AiServiceInvocationContext::interfaceName,
                        AiServiceInvocationContext::methodName,
                        AiServiceInvocationContext::methodArguments,
                        AiServiceInvocationContext::memoryId)
                .containsExactly(
                        Assistant.class.getName(), expectedMethodName, List.of(expectedUserMessage), Optional.empty());

        assertThat(is.interactionId()).isNotNull();
        assertThat(is.timestamp()).isNotNull();
    }

    private static Map<Class<? extends AiServiceInvocationEvent>, MyEventListener<? extends AiServiceInvocationEvent>>
            createListeners() {

        return Stream.of(
                        new MyInputGuardrailExecutedEventListener(),
                        new MyAiServiceInteractionCompletedEventListener(),
                        new MyAiServiceInteractionErrorEventListener(),
                        new MyAiServiceInteractionStartedEventListener(),
                        new MyAiServiceResponseReceivedEventListener(),
                        new MyOutputGuardrailExecutedEventListener(),
                        new MyToolExecutedEventListener())
                .collect(Collectors.toMap(AiServiceInvocationEventListener::getEventClass, Function.identity()));
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
            var builder = AiServices.builder(Assistant.class);
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
            return streaming
                    ? AiServices.create(
                            Assistant.class,
                            StreamingChatModelMock.thatAlwaysThrowsExceptionWithMessage("LLM invocation failed"))
                    : AiServices.create(
                            Assistant.class,
                            ChatModelMock.thatAlwaysThrowsExceptionWithMessage("LLM invocation failed"));
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

    public abstract static class MyEventListener<T extends AiServiceInvocationEvent>
            implements AiServiceInvocationEventListener<T> {
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

        public void reset() {
            this.count.set(0);
            this.event = null;
        }
    }

    //    private static class WaitingForCompletionTokenStream implements TokenStream {
    //        private final CountDownLatch latch = new CountDownLatch(1);
    //        private final TokenStream tokenStream;
    //
    //        private WaitingForCompletionTokenStream(TokenStream tokenStream) {
    //            this.tokenStream = ensureNotNull(tokenStream, "tokenStream");
    //        }
    //
    //        @Override
    //        public TokenStream onPartialResponse(Consumer<String> partialResponseHandler) {
    //            return this.tokenStream.onPartialResponse(partialResponseHandler);
    //        }
    //
    //        @Override
    //        public TokenStream onRetrieved(Consumer<List<Content>> contentHandler) {
    //            return this.tokenStream.onRetrieved(contentHandler);
    //        }
    //
    //        @Override
    //        public TokenStream onToolExecuted(Consumer<ToolExecution> toolExecuteHandler) {
    //            return this.tokenStream.onToolExecuted(toolExecuteHandler);
    //        }
    //
    //        @Override
    //        public TokenStream onCompleteResponse(Consumer<ChatResponse> completeResponseHandler) {
    //            return this.tokenStream.onCompleteResponse(wrap(completeResponseHandler));
    //        }
    //
    //        @Override
    //        public TokenStream onError(Consumer<Throwable> errorHandler) {
    //            return this.tokenStream.onError(wrap(errorHandler));
    //        }
    //
    //        @Override
    //        public TokenStream ignoreErrors() {
    //            return this.tokenStream.ignoreErrors();
    //        }
    //
    //        private <T> Consumer<T> wrap(Consumer<T> consumer) {
    //            return t -> {
    //              try {
    //                  consumer.accept(t);
    //              }
    //              finally {
    //                  this.latch.countDown();
    //              }
    //            };
    //        }
    //
    //        @Override
    //        public void start() {
    //            try {
    //                this.tokenStream.start();
    //            }
    //            finally {
    //                try {
    //                    this.latch.await(1, TimeUnit.MINUTES);
    //                } catch (InterruptedException e) {
    //                    throw new RuntimeException(e);
    //                }
    //            }
    //        }
    //    }

    public static class MyToolExecutedEventListener extends MyEventListener<ToolExecutedEvent>
            implements ToolExecutedEventListener {}

    public static class MyOutputGuardrailExecutedEventListener extends MyEventListener<OutputGuardrailExecutedEvent>
            implements OutputGuardrailExecutedEventListener {}

    public static class MyAiServiceResponseReceivedEventListener extends MyEventListener<AiServiceResponseReceivedEvent>
            implements AiServiceResponseReceivedEventListener {}

    public static class MyAiServiceInteractionStartedEventListener
            extends MyEventListener<AiServiceInvocationStartedEvent>
            implements AiServiceInvocationStartedEventListener {}

    public static class MyAiServiceInteractionErrorEventListener extends MyEventListener<AiServiceInvocationErrorEvent>
            implements AiServiceInvocationErrorEventListener {}

    public static class MyAiServiceInteractionCompletedEventListener
            extends MyEventListener<AiServiceInvocationCompletedEvent>
            implements AiServiceInvocationCompletedEventListener {}

    public static class MyInputGuardrailExecutedEventListener extends MyEventListener<InputGuardrailExecutedEvent>
            implements InputGuardrailExecutedEventListener {}
}
