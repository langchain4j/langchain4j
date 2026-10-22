package dev.langchain4j.service;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import dev.langchain4j.agent.tool.CompensateFor;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.guardrail.InputGuardrail;
import dev.langchain4j.guardrail.InputGuardrailRequest;
import dev.langchain4j.guardrail.InputGuardrailResult;
import dev.langchain4j.guardrail.OutputGuardrail;
import dev.langchain4j.guardrail.OutputGuardrailRequest;
import dev.langchain4j.guardrail.OutputGuardrailResult;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.mock.ChatModelMock;
import dev.langchain4j.model.chat.mock.StreamingChatModelMock;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingHandle;
import dev.langchain4j.observability.api.event.AiServiceCancelledEvent;
import dev.langchain4j.observability.api.event.AiServiceCompletedEvent;
import dev.langchain4j.observability.api.event.AiServiceErrorEvent;
import dev.langchain4j.observability.api.event.AiServiceEvent;
import dev.langchain4j.observability.api.event.AiServiceInvocationSummaryEvent;
import dev.langchain4j.observability.api.event.AiServiceRequestIssuedEvent;
import dev.langchain4j.observability.api.event.AiServiceResponseReceivedEvent;
import dev.langchain4j.observability.api.event.AiServiceStartedEvent;
import dev.langchain4j.observability.api.event.OutputGuardrailExecutedEvent;
import dev.langchain4j.observability.api.event.RetrievalAugmentedEvent;
import dev.langchain4j.observability.api.event.ToolExecutedEvent;
import dev.langchain4j.observability.api.listener.AiServiceInvocationSummaryListener;
import dev.langchain4j.observability.api.listener.AiServiceListener;
import dev.langchain4j.rag.DefaultRetrievalAugmentor;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.service.tool.ToolErrorHandlerResult;
import dev.langchain4j.service.tool.ToolExecutionErrorHandler;
import dev.langchain4j.service.tool.ToolExecutor;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

class AiServiceInvocationEventsTest {

    interface Assistant {

        String chat(String message);
    }

    interface StreamingAssistant {

        TokenStream chat(String message);
    }

    interface AsyncAssistant {

        CompletableFuture<String> chat(String message);
    }

    interface ReactiveAssistant {

        Flow.Publisher<String> chat(String message);
    }

    interface ReactiveEventsAssistant {

        Flow.Publisher<AiServiceStreamingEvent> chat(String message);
    }

    final List<AiServiceEvent> events = new CopyOnWriteArrayList<>();
    final CountDownLatch finished = new CountDownLatch(1);
    final CountDownLatch twoTerminalEvents = new CountDownLatch(2);
    volatile Consumer<AiServiceEvent> onTerminalEvent = event -> {};

    @Test
    void should_report_whether_tool_execution_failed() {
        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(ChatModelMock.thatResponds(callToolsThenAnswer("succeed", "fail")))
                .tools(new Tools())
                .registerListeners(listeners())
                .build();

        assistant.chat("Hello");

        assertThat(events(ToolExecutedEvent.class))
                .extracting(
                        event -> event.request().name(),
                        ToolExecutedEvent::isError)
                .containsExactly(
                        tuple("succeed", false),
                        tuple("fail", true));
    }

    @Test
    void should_report_retrieved_content() {
        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(ChatModelMock.thatAlwaysResponds("answer"))
                .contentRetriever(query -> List.of(Content.from("source")))
                .registerListeners(listeners())
                .build();

        assistant.chat("Hello");

        assertThat(events(RetrievalAugmentedEvent.class)).singleElement().satisfies(event -> {
            assertThat(event.chatMessage()).isEqualTo(UserMessage.from("Hello"));
            assertThat(event.contents())
                    .extracting(content -> content.textSegment().text())
                    .containsExactly("source");
        });
    }

    @Test
    void should_report_that_nothing_was_retrieved() {
        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(ChatModelMock.thatAlwaysResponds("answer"))
                .contentRetriever(query -> List.of())
                .registerListeners(listeners())
                .build();

        assistant.chat("Hello");

        assertThat(events(RetrievalAugmentedEvent.class))
                .singleElement()
                .satisfies(event -> assertThat(event.contents()).isEmpty());
    }

    @Test
    void should_report_retrieved_content_of_async_invocation() throws Exception {
        AsyncAssistant assistant = AiServices.builder(AsyncAssistant.class)
                .chatModel(ChatModelMock.thatAlwaysResponds("answer"))
                .retrievalAugmentor(offloading(query -> List.of(Content.from("source"))))
                .registerListeners(listeners())
                .build();

        assistant.chat("Hello").get(5, SECONDS);

        assertThat(events(RetrievalAugmentedEvent.class))
                .singleElement()
                .satisfies(event -> assertThat(event.chatMessage()).isEqualTo(UserMessage.from("Hello")));
    }

    @Test
    void should_fire_error_event_when_token_stream_ignores_errors() throws Exception {
        StreamingAssistant assistant = AiServices.builder(StreamingAssistant.class)
                .streamingChatModel(StreamingChatModelMock.thatAlwaysThrowsExceptionWithMessage("boom"))
                .registerListeners(listeners())
                .build();

        assistant.chat("Hello").ignoreErrors().start();

        assertSingleTerminalEvent(AiServiceErrorEvent.class);
        assertThat(events(AiServiceErrorEvent.class))
                .singleElement()
                .satisfies(event -> assertThat(event.error()).hasMessage("boom"));
    }

    @Test
    void should_fire_cancelled_event_when_token_stream_is_cancelled() throws Exception {
        StreamingAssistant assistant = AiServices.builder(StreamingAssistant.class)
                .streamingChatModel(StreamingChatModelMock.thatAlwaysStreams("a", "b", "c"))
                .registerListeners(listeners())
                .build();

        assistant
                .chat("Hello")
                .onPartialResponseWithContext(
                        (partialResponse, context) -> context.streamingHandle().cancel())
                .onError(error -> {})
                .start();

        assertSingleTerminalEvent(AiServiceCancelledEvent.class);
    }

    @Test
    void should_fire_cancelled_event_when_future_is_cancelled() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        AsyncAssistant assistant = AiServices.builder(AsyncAssistant.class)
                .chatModel(ChatModelMock.thatResponds(request -> {
                    await(release);
                    return AiMessage.from("answer");
                }))
                .registerListeners(listeners())
                .build();

        CompletableFuture<String> future = assistant.chat("Hello");
        future.cancel(true);
        release.countDown();

        assertSingleTerminalEvent(AiServiceCancelledEvent.class);
    }

    @Test
    void should_fire_cancelled_event_when_subscription_is_cancelled() throws Exception {
        ReactiveAssistant assistant = AiServices.builder(ReactiveAssistant.class)
                .streamingChatModel(StreamingChatModelMock.thatAlwaysStreams("a", "b", "c"))
                .registerListeners(listeners())
                .build();

        assistant.chat("Hello").subscribe(new Flow.Subscriber<>() {

            private Flow.Subscription subscription;

            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                this.subscription = subscription;
                subscription.request(1);
            }

            @Override
            public void onNext(String item) {
                subscription.cancel();
            }

            @Override
            public void onError(Throwable throwable) {}

            @Override
            public void onComplete() {}
        });

        assertSingleTerminalEvent(AiServiceCancelledEvent.class);
    }

    @Test
    void should_not_fire_cancelled_event_when_subscription_is_cancelled_after_completion() throws Exception {
        ReactiveAssistant assistant = AiServices.builder(ReactiveAssistant.class)
                .streamingChatModel(StreamingChatModelMock.thatAlwaysStreams("a"))
                .registerListeners(listeners())
                .build();
        CompletableFuture<Flow.Subscription> subscription = new CompletableFuture<>();

        assistant.chat("Hello").subscribe(new Flow.Subscriber<>() {

            @Override
            public void onSubscribe(Flow.Subscription s) {
                subscription.complete(s);
                s.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(String item) {}

            @Override
            public void onError(Throwable throwable) {}

            @Override
            public void onComplete() {}
        });
        assertThat(finished.await(5, SECONDS)).isTrue();
        subscription.get(5, SECONDS).cancel();

        assertSingleTerminalEvent(AiServiceCompletedEvent.class);
    }

    @Test
    void should_fire_error_event_when_async_retrieval_fails() throws Exception {
        AsyncAssistant assistant = AiServices.builder(AsyncAssistant.class)
                .chatModel(ChatModelMock.thatAlwaysResponds("answer"))
                .retrievalAugmentor(offloading(query -> {
                    throw new IllegalStateException("retrieval failed");
                }))
                .registerListeners(listeners())
                .build();

        CompletableFuture<String> future = assistant.chat("Hello");

        assertThat(finished.await(5, SECONDS)).isTrue();
        assertThat(future).failsWithin(5, SECONDS); // the error event is fired before the future completes
        assertThat(events(AiServiceErrorEvent.class))
                .singleElement()
                .satisfies(event -> assertThat(event.error()).hasMessageContaining("retrieval failed"));
    }

    @Test
    void should_fire_error_event_when_reactive_retrieval_fails() throws Exception {
        ReactiveAssistant assistant = AiServices.builder(ReactiveAssistant.class)
                .streamingChatModel(StreamingChatModelMock.thatAlwaysStreams("a"))
                .retrievalAugmentor(offloading(query -> {
                    throw new IllegalStateException("retrieval failed");
                }))
                .registerListeners(listeners())
                .build();
        CompletableFuture<Throwable> error = new CompletableFuture<>();

        assistant.chat("Hello").subscribe(new Flow.Subscriber<>() {

            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(String item) {}

            @Override
            public void onError(Throwable throwable) {
                error.complete(throwable);
            }

            @Override
            public void onComplete() {
                error.complete(null);
            }
        });

        assertThat(error.get(5, SECONDS)).hasMessageContaining("retrieval failed");
        assertThat(events(AiServiceErrorEvent.class))
                .singleElement()
                .satisfies(event -> assertThat(event.error()).hasMessageContaining("retrieval failed"));
    }

    @Test
    void should_fire_one_response_received_event_per_request_when_output_guardrail_reprompts() {
        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(ChatModelMock.thatAlwaysResponds(AiMessage.from("bad"), AiMessage.from("good")))
                .outputGuardrails(repromptingOnce())
                .registerListeners(listeners())
                .build();

        assertThat(assistant.chat("Hello")).isEqualTo("good");

        assertRequestsAndResponsesArePaired();
    }

    @Test
    void should_fire_one_response_received_event_per_request_when_output_guardrail_reprompts_async()
            throws Exception {
        AsyncAssistant assistant = AiServices.builder(AsyncAssistant.class)
                .chatModel(ChatModelMock.thatAlwaysResponds(AiMessage.from("bad"), AiMessage.from("good")))
                .outputGuardrails(repromptingOnce())
                .registerListeners(listeners())
                .build();

        assertThat(assistant.chat("Hello").get(5, SECONDS)).isEqualTo("good");

        assertRequestsAndResponsesArePaired();
    }

    @Test
    void should_fire_one_response_received_event_per_request_when_output_guardrail_reprompts_token_stream()
            throws Exception {
        StreamingAssistant assistant = AiServices.builder(StreamingAssistant.class)
                .streamingChatModel(
                        StreamingChatModelMock.thatAlwaysStreams(AiMessage.from("bad"), AiMessage.from("good")))
                .outputGuardrails(repromptingOnce())
                .registerListeners(listeners())
                .build();
        CompletableFuture<String> answer = new CompletableFuture<>();

        assistant
                .chat("Hello")
                .onCompleteResponse(response -> answer.complete(response.aiMessage().text()))
                .onError(answer::completeExceptionally)
                .start();

        assertThat(answer.get(5, SECONDS)).isEqualTo("good");
        assertSingleTerminalEvent(AiServiceCompletedEvent.class);
        assertRequestsAndResponsesArePaired();
    }

    @Test
    void should_fire_one_response_received_event_per_request_when_output_guardrail_reprompts_reactive_invocation()
            throws Exception {
        ReactiveAssistant assistant = AiServices.builder(ReactiveAssistant.class)
                .streamingChatModel(
                        StreamingChatModelMock.thatAlwaysStreams(AiMessage.from("bad"), AiMessage.from("good")))
                .outputGuardrails(repromptingOnce())
                .registerListeners(listeners())
                .build();

        consume(assistant.chat("Hello")).get(5, SECONDS);

        assertSingleTerminalEvent(AiServiceCompletedEvent.class);
        assertRequestsAndResponsesArePaired();
    }

    @Test
    void should_report_a_failed_tool_execution_when_the_model_calls_an_unknown_tool() {
        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(ChatModelMock.thatResponds(callToolsThenAnswer("unknown")))
                .tools(new Tools())
                .registerListeners(listeners())
                .build();

        assertThatThrownBy(() -> assistant.chat("Hello")).hasMessageContaining("unknown");

        assertFailedToolExecutionReported("unknown", "unknown");
    }

    @Test
    void should_report_a_failed_tool_execution_when_the_model_calls_an_unknown_tool_in_a_reactive_invocation() {
        ReactiveAssistant assistant = AiServices.builder(ReactiveAssistant.class)
                .streamingChatModel(StreamingChatModelMock.thatAlwaysStreams(
                        AiMessage.from(ToolExecutionRequest.builder()
                                .id("1")
                                .name("unknown")
                                .arguments("{}")
                                .build()),
                        AiMessage.from("answer")))
                .tools(new Tools())
                .registerListeners(listeners())
                .build();

        assertThat(consume(assistant.chat("Hello"))).failsWithin(5, SECONDS);

        assertFailedToolExecutionReported("unknown", "unknown");
    }

    @Test
    void should_report_the_error_flag_returned_by_the_hallucinated_tool_name_strategy() {
        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(ChatModelMock.thatResponds(callToolsThenAnswer("unknown")))
                .tools(new Tools())
                .hallucinatedToolNameStrategy(request -> ToolExecutionResultMessage.builder()
                        .id(request.id())
                        .toolName(request.name())
                        .text("There is no such tool")
                        .isError(true)
                        .build())
                .registerListeners(listeners())
                .build();

        assertThat(assistant.chat("Hello")).isEqualTo("answer");

        assertFailedToolExecutionReported("unknown", "There is no such tool");
    }

    @Test
    void should_report_a_failed_tool_execution_when_the_arguments_error_handler_fails_the_invocation() {
        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(ChatModelMock.thatResponds(request -> {
                    List<ChatMessage> messages = request.messages();
                    if (messages.get(messages.size() - 1) instanceof ToolExecutionResultMessage) {
                        return AiMessage.from("answer");
                    }
                    return AiMessage.from(ToolExecutionRequest.builder()
                            .id("1")
                            .name("echo")
                            .arguments("{ not json")
                            .build());
                }))
                .tools(new Tools())
                .registerListeners(listeners())
                .build();

        assertThatThrownBy(() -> assistant.chat("Hello"))
                .isInstanceOf(RuntimeException.class) // the exact type depends on the JSON codec
                .hasMessageContaining("Unexpected character");

        assertThat(events(ToolExecutedEvent.class)).singleElement().satisfies(event -> {
            assertThat(event.request().name()).isEqualTo("echo");
            assertThat(event.isError()).isTrue();
        });
    }

    @Test
    void should_report_every_tool_execution_when_concurrent_tools_fail_a_reactive_invocation() {
        ReactiveAssistant assistant = AiServices.builder(ReactiveAssistant.class)
                .streamingChatModel(StreamingChatModelMock.thatAlwaysStreams(
                        AiMessage.from(List.of(
                                ToolExecutionRequest.builder()
                                        .id("1")
                                        .name("succeed")
                                        .arguments("{}")
                                        .build(),
                                ToolExecutionRequest.builder()
                                        .id("2")
                                        .name("fail")
                                        .arguments("{}")
                                        .build())),
                        AiMessage.from("answer")))
                .tools(new Tools())
                .executeToolsConcurrently()
                .toolExecutionErrorHandler(ToolExecutionErrorHandler.failInvocation())
                .registerListeners(listeners())
                .build();

        assertThat(consume(assistant.chat("Hello"))).failsWithin(5, SECONDS);

        assertThat(events(ToolExecutedEvent.class))
                .extracting(event -> event.request().name(), ToolExecutedEvent::isError)
                .containsExactlyInAnyOrder(
                        tuple("succeed", false),
                        tuple("fail", true));
    }

    @Test
    void should_report_the_tools_that_ran_before_a_sequential_tool_failed_the_invocation() {
        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(ChatModelMock.thatResponds(callToolsThenAnswer("succeed", "fail")))
                .tools(new Tools())
                .toolExecutionErrorHandler(ToolExecutionErrorHandler.failInvocation())
                .registerListeners(listeners())
                .build();

        assertThatThrownBy(() -> assistant.chat("Hello")).hasMessageContaining("tool failed");

        assertToolExecutionsReported(true);
    }

    @Test
    void should_report_the_tools_that_ran_when_a_concurrent_tool_failed_the_invocation() {
        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(ChatModelMock.thatResponds(callToolsThenAnswer("succeed", "fail")))
                .tools(new Tools())
                .executeToolsConcurrently()
                .toolExecutionErrorHandler(ToolExecutionErrorHandler.failInvocation())
                .registerListeners(listeners())
                .build();

        assertThatThrownBy(() -> assistant.chat("Hello")).hasMessageContaining("tool failed");

        assertToolExecutionsReported(true);
    }

    @Test
    void should_report_the_tools_that_ran_before_a_sequential_tool_failed_a_token_stream() {
        StreamingAssistant assistant = AiServices.builder(StreamingAssistant.class)
                .streamingChatModel(
                        StreamingChatModelMock.thatAlwaysStreams(callingTools("succeed", "fail"), AiMessage.from("answer")))
                .tools(new Tools())
                .toolExecutionErrorHandler(ToolExecutionErrorHandler.failInvocation())
                .registerListeners(listeners())
                .build();

        assertThat(start(assistant.chat("Hello"))).failsWithin(5, SECONDS);

        assertToolExecutionsReported(true);
    }

    @Test
    void should_report_the_tools_that_ran_when_a_concurrent_tool_failed_a_token_stream() {
        StreamingAssistant assistant = AiServices.builder(StreamingAssistant.class)
                .streamingChatModel(
                        StreamingChatModelMock.thatAlwaysStreams(callingTools("succeed", "fail"), AiMessage.from("answer")))
                .tools(new Tools())
                .executeToolsConcurrently()
                .toolExecutionErrorHandler(ToolExecutionErrorHandler.failInvocation())
                .registerListeners(listeners())
                .build();

        assertThat(start(assistant.chat("Hello"))).failsWithin(5, SECONDS);

        assertToolExecutionsReported(false);
    }

    @Test
    void should_report_each_start_of_a_token_stream_as_a_separate_invocation() throws Exception {
        StreamingAssistant assistant = AiServices.builder(StreamingAssistant.class)
                .streamingChatModel(StreamingChatModelMock.thatAlwaysStreams("Hel", "lo"))
                .registerListeners(listeners())
                .build();
        CountDownLatch completedTwice = new CountDownLatch(2);
        TokenStream tokenStream = assistant
                .chat("Hello")
                .onPartialResponse(partialResponse -> {})
                .onCompleteResponse(response -> completedTwice.countDown())
                .onError(error -> {});

        tokenStream.start();
        tokenStream.start();

        assertThat(completedTwice.await(5, SECONDS)).isTrue();

        List<AiServiceStartedEvent> started = events(AiServiceStartedEvent.class);
        List<AiServiceCompletedEvent> completed = events(AiServiceCompletedEvent.class);
        assertThat(started).hasSize(2);
        assertThat(completed).hasSize(2);
        assertThat(started.get(0).invocationContext().invocationId())
                .isNotEqualTo(started.get(1).invocationContext().invocationId());
        assertThat(completed)
                .extracting(event -> event.invocationContext().invocationId())
                .containsExactlyInAnyOrder( // the two runs stream concurrently
                        started.get(0).invocationContext().invocationId(),
                        started.get(1).invocationContext().invocationId());
        assertThat(started.get(1).userMessage()).isEqualTo(started.get(0).userMessage());
    }

    @Test
    void should_not_fire_cancelled_event_when_future_is_cancelled_after_completed_event() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        AtomicReference<CompletableFuture<String>> future = new AtomicReference<>();
        onTerminalEvent = event -> {
            if (event instanceof AiServiceCompletedEvent) {
                future.get().cancel(true); // after the completed event, before the future completes
            }
        };
        AsyncAssistant assistant = AiServices.builder(AsyncAssistant.class)
                .chatModel(ChatModelMock.thatResponds(request -> {
                    await(release);
                    return AiMessage.from("answer");
                }))
                .registerListeners(listeners())
                .build();

        future.set(assistant.chat("Hello"));
        release.countDown();

        assertThatThrownBy(() -> future.get().get(5, SECONDS)).isInstanceOf(CancellationException.class);
        assertSingleTerminalEvent(AiServiceCompletedEvent.class);
    }

    @Test
    void should_not_fire_completed_event_when_future_is_cancelled_during_output_guardrail() throws Exception {
        CountDownLatch chatReturned = new CountDownLatch(1);
        CountDownLatch inGuardrail = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AsyncAssistant assistant = AiServices.builder(AsyncAssistant.class)
                // the model answers only after chat(...) returned, so the guardrail never runs on the test thread
                .chatModel(ChatModelMock.thatResponds(request -> {
                    await(chatReturned);
                    return AiMessage.from("answer");
                }))
                .outputGuardrails(new OutputGuardrail() {
                    @Override
                    public CompletableFuture<OutputGuardrailResult> validateAsync(OutputGuardrailRequest request) {
                        inGuardrail.countDown();
                        await(release);
                        return CompletableFuture.completedFuture(success());
                    }
                })
                .registerListeners(listeners())
                .build();

        CompletableFuture<String> future = assistant.chat("Hello");
        chatReturned.countDown();
        assertThat(inGuardrail.await(5, SECONDS)).isTrue();
        future.cancel(true);
        release.countDown();

        assertSingleTerminalEvent(AiServiceCancelledEvent.class);
    }

    @Test
    void should_not_fire_cancelled_event_when_subscriber_cancels_after_completed_event_was_fired() throws Exception {
        ReactiveAssistant assistant = AiServices.builder(ReactiveAssistant.class)
                .streamingChatModel(StreamingChatModelMock.thatAlwaysStreams("a", "b", "c"))
                .registerListeners(listeners())
                .build();
        AtomicReference<Flow.Subscription> subscription = new AtomicReference<>();

        assistant.chat("Hello").subscribe(new Flow.Subscriber<>() {

            @Override
            public void onSubscribe(Flow.Subscription s) {
                subscription.set(s);
                s.request(1); // bounded demand: the final response is not delivered before the cancellation
            }

            @Override
            public void onNext(String item) {}

            @Override
            public void onError(Throwable throwable) {}

            @Override
            public void onComplete() {}
        });

        assertThat(finished.await(5, SECONDS)).isTrue();
        subscription.get().cancel();

        assertSingleTerminalEvent(AiServiceCompletedEvent.class);
    }

    @Test
    void should_fire_one_terminal_event_when_token_stream_is_cancelled_between_tool_rounds() throws Exception {
        StreamingAssistant assistant = AiServices.builder(StreamingAssistant.class)
                .streamingChatModel(StreamingChatModelMock.thatAlwaysStreams(
                        AiMessage.from(
                                "Checking",
                                List.of(ToolExecutionRequest.builder()
                                        .id("1")
                                        .name("succeed")
                                        .arguments("{}")
                                        .build())),
                        AiMessage.from("Done")))
                .tools(new Tools())
                .registerListeners(listeners())
                .build();
        AtomicReference<StreamingHandle> firstRoundHandle = new AtomicReference<>();
        CountDownLatch toolExecuted = new CountDownLatch(1);

        assistant
                .chat("Hello")
                .onPartialResponseWithContext(
                        (partialResponse, context) -> firstRoundHandle.compareAndSet(null, context.streamingHandle()))
                .beforeToolExecution(beforeToolExecution -> firstRoundHandle.get().cancel())
                .onToolExecuted(toolExecution -> toolExecuted.countDown())
                .onError(error -> {})
                .start();

        assertThat(toolExecuted.await(5, SECONDS)).isTrue();
        assertSingleTerminalEvent(AiServiceCancelledEvent.class);
    }

    private static OutputGuardrail repromptingOnce() {
        return new OutputGuardrail() {
            @Override
            public OutputGuardrailResult validate(AiMessage responseFromLLM) {
                return "bad".equals(responseFromLLM.text()) ? reprompt("bad answer", "Try again") : success();
            }

            @Override
            public CompletableFuture<OutputGuardrailResult> validateAsync(OutputGuardrailRequest request) {
                return CompletableFuture.completedFuture(validate(request));
            }
        };
    }

    private void assertRequestsAndResponsesArePaired() {
        List<AiServiceEvent> modelEvents = events.stream()
                .filter(event -> event instanceof AiServiceRequestIssuedEvent
                        || event instanceof AiServiceResponseReceivedEvent)
                .toList();
        assertThat(modelEvents)
                .<Class<?>>extracting(AiServiceEvent::eventClass)
                .containsExactly(
                        AiServiceRequestIssuedEvent.class,
                        AiServiceResponseReceivedEvent.class,
                        AiServiceRequestIssuedEvent.class,
                        AiServiceResponseReceivedEvent.class);
        for (int i = 0; i < modelEvents.size(); i += 2) {
            AiServiceRequestIssuedEvent issued = (AiServiceRequestIssuedEvent) modelEvents.get(i);
            AiServiceResponseReceivedEvent received = (AiServiceResponseReceivedEvent) modelEvents.get(i + 1);
            assertThat(received.request()).isEqualTo(issued.request());
        }
        assertThat(((AiServiceResponseReceivedEvent) modelEvents.get(1)).response().aiMessage().text())
                .isEqualTo("bad");
        assertThat(((AiServiceResponseReceivedEvent) modelEvents.get(3)).response().aiMessage().text())
                .isEqualTo("good");
    }

    @Test
    void should_fire_started_event_before_reactive_subscriber_can_cancel() throws Exception {
        ReactiveEventsAssistant assistant = AiServices.builder(ReactiveEventsAssistant.class)
                .streamingChatModel(StreamingChatModelMock.thatAlwaysStreams("a"))
                .registerListeners(listeners())
                .build();
        List<Class<?>> eventsBeforeOnSubscribe = new CopyOnWriteArrayList<>();

        assistant.chat("Hello").subscribe(new Flow.Subscriber<>() {

            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                events.forEach(event -> eventsBeforeOnSubscribe.add(event.eventClass()));
                subscription.cancel();
            }

            @Override
            public void onNext(AiServiceStreamingEvent item) {}

            @Override
            public void onError(Throwable throwable) {}

            @Override
            public void onComplete() {}
        });

        assertSingleTerminalEvent(AiServiceCancelledEvent.class);
        assertThat(eventsBeforeOnSubscribe).containsExactly(AiServiceStartedEvent.class);
    }

    @Test
    void should_fire_error_event_when_input_guardrail_of_async_invocation_fails() throws Exception {
        AsyncAssistant assistant = AiServices.builder(AsyncAssistant.class)
                .chatModel(ChatModelMock.thatAlwaysResponds("answer"))
                .inputGuardrails(blockingGuardrail())
                .registerListeners(listeners())
                .build();

        CompletableFuture<String> future = assistant.chat("Hello");

        assertThat(future).failsWithin(5, SECONDS);
        assertSingleTerminalEvent(AiServiceErrorEvent.class);
        assertThat(events(AiServiceErrorEvent.class))
                .singleElement()
                .satisfies(event -> assertThat(event.error()).hasMessageContaining("blocked"));
    }

    @Test
    void should_fire_error_event_when_input_guardrail_of_reactive_invocation_fails() throws Exception {
        ReactiveAssistant assistant = AiServices.builder(ReactiveAssistant.class)
                .streamingChatModel(StreamingChatModelMock.thatAlwaysStreams("a"))
                .inputGuardrails(blockingGuardrail())
                .registerListeners(listeners())
                .build();

        assistant.chat("Hello").subscribe(new Flow.Subscriber<>() {

            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(String item) {}

            @Override
            public void onError(Throwable throwable) {}

            @Override
            public void onComplete() {}
        });

        assertSingleTerminalEvent(AiServiceErrorEvent.class);
        assertThat(events(AiServiceErrorEvent.class))
                .singleElement()
                .satisfies(event -> assertThat(event.error()).hasMessageContaining("blocked"));
    }

    @Test
    void should_report_retrieved_content_of_token_stream() throws Exception {
        StreamingAssistant assistant = AiServices.builder(StreamingAssistant.class)
                .streamingChatModel(StreamingChatModelMock.thatAlwaysStreams("a"))
                .contentRetriever(query -> List.of(Content.from("source")))
                .registerListeners(listeners())
                .build();

        assistant.chat("Hello").onError(error -> {}).start();

        assertThat(finished.await(5, SECONDS)).isTrue();
        assertRetrievedSource();
    }

    @Test
    void should_report_retrieved_content_of_reactive_invocation() throws Exception {
        ReactiveAssistant assistant = AiServices.builder(ReactiveAssistant.class)
                .streamingChatModel(StreamingChatModelMock.thatAlwaysStreams("a"))
                .retrievalAugmentor(offloading(query -> List.of(Content.from("source"))))
                .registerListeners(listeners())
                .build();

        assistant.chat("Hello").subscribe(new Flow.Subscriber<>() {

            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(String item) {}

            @Override
            public void onError(Throwable throwable) {}

            @Override
            public void onComplete() {}
        });

        assertThat(finished.await(5, SECONDS)).isTrue();
        assertRetrievedSource();
    }

    @Test
    void should_fire_error_event_when_model_throws_an_error() {
        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(ChatModelMock.thatResponds(request -> {
                    throw new AssertionError("unexpected request");
                }))
                .registerListeners(listeners())
                .build();

        assertThatThrownBy(() -> assistant.chat("Hello"))
                .isExactlyInstanceOf(AssertionError.class)
                .hasMessage("unexpected request");

        assertThat(events(AiServiceErrorEvent.class))
                .singleElement()
                .satisfies(event -> assertThat(event.error()).hasMessage("unexpected request"));
    }

    @Test
    void should_fire_error_event_when_streaming_model_throws_from_chat() {
        StreamingAssistant assistant = AiServices.builder(StreamingAssistant.class)
                .streamingChatModel(StreamingChatModelMock.thatAlwaysThrowsExceptionWithMessage("cannot connect")
                        .throwingFromChat())
                .registerListeners(listeners())
                .build();
        TokenStream tokenStream = assistant.chat("Hello").onError(error -> {});

        assertThatThrownBy(tokenStream::start).hasMessage("cannot connect");

        assertThat(events(AiServiceErrorEvent.class))
                .singleElement()
                .satisfies(event -> assertThat(event.error()).hasMessage("cannot connect"));
    }

    @Test
    void should_fire_error_event_when_chat_request_transformer_of_token_stream_fails() throws Exception {
        StreamingAssistant assistant = AiServices.builder(StreamingAssistant.class)
                .streamingChatModel(StreamingChatModelMock.thatAlwaysStreams("a"))
                .chatRequestTransformer(chatRequest -> {
                    throw new IllegalStateException("transformer failed");
                })
                .registerListeners(listeners())
                .build();
        TokenStream tokenStream = assistant.chat("Hello").onPartialResponse(token -> {}).onError(error -> {});

        assertThatThrownBy(tokenStream::start).hasMessage("transformer failed");

        assertSingleTerminalEvent(AiServiceErrorEvent.class);
        assertThat(events(AiServiceErrorEvent.class))
                .singleElement()
                .satisfies(event -> assertThat(event.error()).hasMessage("transformer failed"));
    }

    @Test
    void should_fire_error_event_when_retrieved_contents_handler_of_token_stream_fails() throws Exception {
        StreamingAssistant assistant = AiServices.builder(StreamingAssistant.class)
                .streamingChatModel(StreamingChatModelMock.thatAlwaysStreams("a"))
                .contentRetriever(query -> List.of(Content.from("source")))
                .registerListeners(listeners())
                .build();
        TokenStream tokenStream = assistant.chat("Hello")
                .onRetrieved(contents -> {
                    throw new IllegalStateException("handler failed");
                })
                .onPartialResponse(token -> {})
                .onError(error -> {});

        assertThatThrownBy(tokenStream::start).hasMessage("handler failed");

        assertSingleTerminalEvent(AiServiceErrorEvent.class);
        assertThat(events(AiServiceErrorEvent.class))
                .singleElement()
                .satisfies(event -> assertThat(event.error()).hasMessage("handler failed"));
    }

    @Test
    void should_end_token_stream_invocation_with_error_event_on_configuration_error_and_start_a_new_one_when_started_again()
            throws Exception {
        StreamingAssistant assistant = AiServices.builder(StreamingAssistant.class)
                .streamingChatModel(StreamingChatModelMock.thatAlwaysStreams("a"))
                .registerListeners(listeners())
                .build();
        TokenStream tokenStream = assistant.chat("Hello").onPartialResponse(token -> {});

        assertThatThrownBy(tokenStream::start).isInstanceOf(IllegalConfigurationException.class);

        assertSingleTerminalEvent(AiServiceErrorEvent.class);
        AiServiceErrorEvent error = events(AiServiceErrorEvent.class).get(0);
        assertThat(error.error()).isInstanceOf(IllegalConfigurationException.class);
        assertThat(events(AiServiceStartedEvent.class))
                .singleElement()
                .satisfies(started -> assertThat(started.invocationContext().invocationId())
                        .isEqualTo(error.invocationContext().invocationId()));

        CountDownLatch completed = new CountDownLatch(1);
        tokenStream
                .onCompleteResponse(response -> completed.countDown())
                .onError(e -> {})
                .start();
        assertThat(completed.await(5, SECONDS)).isTrue();

        assertThat(events(AiServiceCompletedEvent.class))
                .singleElement()
                .satisfies(completedEvent -> assertThat(completedEvent.invocationContext().invocationId())
                        .isNotEqualTo(error.invocationContext().invocationId()));
        assertThat(events(AiServiceStartedEvent.class))
                .hasSize(2)
                .extracting(started -> started.invocationContext().invocationId())
                .containsExactly(
                        error.invocationContext().invocationId(),
                        events(AiServiceCompletedEvent.class).get(0).invocationContext().invocationId());
        assertThat(events(AiServiceErrorEvent.class)).hasSize(1);
    }

    @Test
    void should_fire_error_event_when_chat_memory_of_async_invocation_fails() throws Exception {
        AsyncAssistant assistant = AiServices.builder(AsyncAssistant.class)
                .chatModel(ChatModelMock.thatAlwaysResponds("answer"))
                .chatMemory(failingChatMemory())
                .registerListeners(listeners())
                .build();

        CompletableFuture<String> future = assistant.chat("Hello");

        assertThat(future).failsWithin(5, SECONDS);
        assertSingleTerminalEvent(AiServiceErrorEvent.class);
        assertThat(events(AiServiceErrorEvent.class))
                .singleElement()
                .satisfies(event -> assertThat(event.error()).hasMessageContaining("memory failed"));
    }

    @Test
    void should_fire_error_event_when_chat_memory_of_reactive_invocation_fails() throws Exception {
        ReactiveAssistant assistant = AiServices.builder(ReactiveAssistant.class)
                .streamingChatModel(StreamingChatModelMock.thatAlwaysStreams("a"))
                .chatMemory(failingChatMemory())
                .registerListeners(listeners())
                .build();

        assertThat(consume(assistant.chat("Hello"))).failsWithin(5, SECONDS);

        assertSingleTerminalEvent(AiServiceErrorEvent.class);
        assertThat(events(AiServiceErrorEvent.class))
                .singleElement()
                .satisfies(event -> assertThat(event.error()).hasMessageContaining("memory failed"));
    }

    @Test
    void should_fire_error_event_when_tool_provider_of_async_invocation_fails() throws Exception {
        AsyncAssistant assistant = AiServices.builder(AsyncAssistant.class)
                .chatModel(ChatModelMock.thatAlwaysResponds("answer"))
                .toolProvider(request -> {
                    throw new IllegalStateException("tool provider failed");
                })
                .registerListeners(listeners())
                .build();

        CompletableFuture<String> future = assistant.chat("Hello");

        assertThat(future).failsWithin(5, SECONDS);
        assertSingleTerminalEvent(AiServiceErrorEvent.class);
        assertThat(events(AiServiceErrorEvent.class))
                .singleElement()
                .satisfies(event -> assertThat(event.error()).hasMessageContaining("tool provider failed"));
    }

    @Test
    void should_fire_error_event_when_creating_the_stream_of_reactive_invocation_fails() throws Exception {
        ReactiveAssistant assistant = AiServices.builder(ReactiveAssistant.class)
                .streamingChatModel(StreamingChatModelMock.thatAlwaysStreams("a"))
                .toolProvider(request -> {
                    throw new IllegalStateException("tool provider failed");
                })
                .registerListeners(listeners())
                .build();

        assertThat(consume(assistant.chat("Hello"))).failsWithin(5, SECONDS);

        assertSingleTerminalEvent(AiServiceErrorEvent.class);
        assertThat(events(AiServiceErrorEvent.class))
                .singleElement()
                .satisfies(event -> assertThat(event.error()).hasMessageContaining("tool provider failed"));
    }

    @Test
    void should_give_each_concurrent_subscription_of_reactive_invocation_its_own_invocation() throws Exception {
        ReactiveAssistant assistant = AiServices.builder(ReactiveAssistant.class)
                .streamingChatModel(StreamingChatModelMock.thatAlwaysStreams("a"))
                .registerListeners(listeners())
                .build();

        assertConcurrentSubscriptionsAreSeparateInvocations(assistant.chat("Hello"));
    }

    @Test
    void should_give_each_concurrent_subscription_of_reactive_events_invocation_its_own_invocation()
            throws Exception {
        ReactiveEventsAssistant assistant = AiServices.builder(ReactiveEventsAssistant.class)
                .streamingChatModel(StreamingChatModelMock.thatAlwaysStreams("a"))
                .registerListeners(listeners())
                .build();

        assertConcurrentSubscriptionsAreSeparateInvocations(assistant.chat("Hello"));
    }

    private void assertConcurrentSubscriptionsAreSeparateInvocations(Flow.Publisher<?> publisher)
            throws Exception {
        int subscriptions = 8;
        CountDownLatch allEnded = new CountDownLatch(subscriptions);
        CountDownLatch oneMoreEnded = new CountDownLatch(subscriptions + 1);
        onTerminalEvent = event -> {
            allEnded.countDown();
            oneMoreEnded.countDown();
        };
        CountDownLatch go = new CountDownLatch(1);
        List<CompletableFuture<Void>> done = new ArrayList<>();
        for (int i = 0; i < subscriptions; i++) {
            done.add(CompletableFuture.runAsync(() -> {
                        await(go);
                    })
                    .thenCompose(ignored -> consume(publisher)));
        }

        go.countDown();

        CompletableFuture.allOf(done.toArray(CompletableFuture[]::new)).get(10, SECONDS);
        assertThat(allEnded.await(5, SECONDS)).isTrue();
        assertThat(oneMoreEnded.await(500, MILLISECONDS)).isFalse();
        List<UUID> startedIds = events(AiServiceStartedEvent.class).stream()
                .map(event -> event.invocationContext().invocationId())
                .toList();
        assertThat(startedIds).hasSize(subscriptions).doesNotHaveDuplicates();
        assertThat(terminalEvents())
                .allSatisfy(event -> assertThat(event).isInstanceOf(AiServiceCompletedEvent.class))
                .extracting(event -> event.invocationContext().invocationId())
                .containsExactlyInAnyOrderElementsOf(startedIds);
    }

    private static ChatMemory failingChatMemory() {
        return MessageWindowChatMemory.builder()
                .maxMessages(10)
                .chatMemoryStore(new ChatMemoryStore() {

                    @Override
                    public List<ChatMessage> getMessages(Object memoryId) {
                        throw new IllegalStateException("memory failed");
                    }

                    @Override
                    public void updateMessages(Object memoryId, List<ChatMessage> messages) {}

                    @Override
                    public void deleteMessages(Object memoryId) {}

                    @Override
                    public CompletableFuture<List<ChatMessage>> getMessagesAsync(Object memoryId) {
                        return CompletableFuture.failedFuture(new IllegalStateException("memory failed"));
                    }

                    @Override
                    public CompletableFuture<Void> updateMessagesAsync(Object memoryId, List<ChatMessage> messages) {
                        return CompletableFuture.completedFuture(null);
                    }

                    @Override
                    public CompletableFuture<Void> deleteMessagesAsync(Object memoryId) {
                        return CompletableFuture.completedFuture(null);
                    }
                })
                .build();
    }

    @Test
    void should_give_each_subscription_of_reactive_invocation_its_own_invocation_id() throws Exception {
        ReactiveAssistant assistant = AiServices.builder(ReactiveAssistant.class)
                .streamingChatModel(new StreamingChatModelMock(List.of(AiMessage.from("one"), AiMessage.from("two"))))
                .registerListeners(listeners())
                .build();
        Flow.Publisher<String> publisher = assistant.chat("Hello");

        consume(publisher).get(5, SECONDS);
        consume(publisher).get(5, SECONDS);

        List<AiServiceStartedEvent> started = events(AiServiceStartedEvent.class);
        assertThat(started).hasSize(2);
        assertThat(started.get(0).invocationContext().invocationId())
                .isNotEqualTo(started.get(1).invocationContext().invocationId());
        assertThat(events(AiServiceCompletedEvent.class))
                .extracting(event -> event.invocationContext().invocationId())
                .containsExactly(
                        started.get(0).invocationContext().invocationId(),
                        started.get(1).invocationContext().invocationId());
    }

    @Test
    void should_not_fire_cancelled_event_when_subscriber_cancels_on_final_response() throws Exception {
        ReactiveEventsAssistant assistant = AiServices.builder(ReactiveEventsAssistant.class)
                .streamingChatModel(StreamingChatModelMock.thatAlwaysStreams("a"))
                .registerListeners(listeners())
                .build();
        CountDownLatch cancelled = new CountDownLatch(1);

        assistant.chat("Hello").subscribe(new Flow.Subscriber<>() {

            private Flow.Subscription subscription;

            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                this.subscription = subscription;
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(AiServiceStreamingEvent item) {
                if (item instanceof AiServiceStreamingEvent.FinalResponseEvent) {
                    subscription.cancel();
                    cancelled.countDown();
                }
            }

            @Override
            public void onError(Throwable throwable) {}

            @Override
            public void onComplete() {}
        });

        assertThat(cancelled.await(5, SECONDS)).isTrue();
        assertSingleTerminalEvent(AiServiceCompletedEvent.class);
    }

    @Test
    void should_not_fire_cancelled_event_when_token_stream_is_cancelled_after_completion() throws Exception {
        StreamingAssistant assistant = AiServices.builder(StreamingAssistant.class)
                .streamingChatModel(StreamingChatModelMock.thatAlwaysStreams("a"))
                .registerListeners(listeners())
                .build();
        CompletableFuture<StreamingHandle> handle = new CompletableFuture<>();

        assistant
                .chat("Hello")
                .onPartialResponseWithContext((partialResponse, context) -> handle.complete(context.streamingHandle()))
                .onError(error -> {})
                .start();
        assertThat(finished.await(5, SECONDS)).isTrue();
        handle.get(5, SECONDS).cancel();

        assertSingleTerminalEvent(AiServiceCompletedEvent.class);
    }

    @Test
    void should_report_a_failed_tool_execution_when_the_error_handler_fails_the_invocation() {
        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(ChatModelMock.thatResponds(callToolsThenAnswer("fail")))
                .tools(new Tools())
                .toolExecutionErrorHandler(ToolExecutionErrorHandler.failInvocation())
                .registerListeners(listeners())
                .build();

        assertThatThrownBy(() -> assistant.chat("Hello")).hasMessage("tool failed");

        assertFailedToolExecutionReported();
    }

    @Test
    void should_report_a_failed_tool_execution_when_the_error_handler_fails_an_async_invocation() {
        AsyncAssistant assistant = AiServices.builder(AsyncAssistant.class)
                .chatModel(ChatModelMock.thatResponds(callToolsThenAnswer("fail")))
                .tools(new Tools())
                .toolExecutionErrorHandler(ToolExecutionErrorHandler.failInvocation())
                .registerListeners(listeners())
                .build();

        assertThat(assistant.chat("Hello")).failsWithin(5, SECONDS);

        assertFailedToolExecutionReported();
    }

    @Test
    void should_report_a_failed_tool_execution_when_the_error_handler_fails_a_token_stream() throws Exception {
        StreamingAssistant assistant = AiServices.builder(StreamingAssistant.class)
                .streamingChatModel(StreamingChatModelMock.thatAlwaysStreams(
                        AiMessage.from(ToolExecutionRequest.builder()
                                .id("1")
                                .name("fail")
                                .arguments("{}")
                                .build()),
                        AiMessage.from("answer")))
                .tools(new Tools())
                .toolExecutionErrorHandler(ToolExecutionErrorHandler.failInvocation())
                .registerListeners(listeners())
                .build();
        CompletableFuture<Void> done = new CompletableFuture<>();

        assistant
                .chat("Hello")
                .onCompleteResponse(response -> done.complete(null))
                .onError(done::completeExceptionally)
                .start();

        assertThat(done).failsWithin(5, SECONDS);
        assertFailedToolExecutionReported();
    }

    @Test
    void should_report_a_failed_tool_execution_when_the_error_handler_fails_a_reactive_invocation() {
        ReactiveAssistant assistant = AiServices.builder(ReactiveAssistant.class)
                .streamingChatModel(StreamingChatModelMock.thatAlwaysStreams(
                        AiMessage.from(ToolExecutionRequest.builder()
                                .id("1")
                                .name("fail")
                                .arguments("{}")
                                .build()),
                        AiMessage.from("answer")))
                .tools(new Tools())
                .toolExecutionErrorHandler(ToolExecutionErrorHandler.failInvocation())
                .registerListeners(listeners())
                .build();

        assertThat(consume(assistant.chat("Hello"))).failsWithin(5, SECONDS);

        assertFailedToolExecutionReported();
    }

    @Test
    void should_start_a_token_stream_twice_when_its_context_has_no_user_message() throws Exception {
        AiServiceContext context = AiServiceContext.create(StreamingAssistant.class);
        context.streamingChatModel = StreamingChatModelMock.thatAlwaysStreams("a");
        listeners().forEach(context.eventListenerRegistrar::register);
        // built like an integration that does not pass the original user message in the invocation context
        TokenStream tokenStream = new AiServiceTokenStream(AiServiceTokenStreamParameters.builder()
                .messages(List.of(UserMessage.from("Hello")))
                .context(context)
                .invocationContext(
                        InvocationContext.builder().chatMemoryId("default").build())
                .toolArgumentsErrorHandler((error, errorContext) -> {
                    throw new RuntimeException(error);
                })
                .toolExecutionErrorHandler((error, errorContext) -> ToolErrorHandlerResult.text(error.getMessage()))
                .build());
        CountDownLatch completed = new CountDownLatch(2);
        tokenStream
                .onPartialResponse(partialResponse -> {})
                .onCompleteResponse(response -> completed.countDown())
                .onError(error -> {});

        tokenStream.start();
        tokenStream.start();

        assertThat(completed.await(5, SECONDS)).isTrue();
        assertThat(events(AiServiceErrorEvent.class)).isEmpty();
        assertThat(events(AiServiceCompletedEvent.class)).hasSize(2);
        assertThat(events(AiServiceStartedEvent.class))
                .singleElement()
                .satisfies(event -> assertThat(event.userMessage()).isEqualTo(UserMessage.from("Hello")));
    }

    @Test
    void should_report_output_guardrail_events_of_a_restarted_token_stream_with_its_own_invocation_id()
            throws Exception {
        StreamingAssistant assistant = AiServices.builder(StreamingAssistant.class)
                .streamingChatModel(StreamingChatModelMock.thatAlwaysStreams("good"))
                .outputGuardrails(repromptingOnce())
                .registerListeners(listeners())
                .registerListener(listener(OutputGuardrailExecutedEvent.class))
                .build();
        TokenStream tokenStream = assistant.chat("Hello");
        CountDownLatch firstRun = new CountDownLatch(1);
        CountDownLatch bothRuns = new CountDownLatch(2);
        tokenStream
                .onPartialResponse(partialResponse -> {})
                .onCompleteResponse(response -> {
                    firstRun.countDown();
                    bothRuns.countDown();
                })
                .onError(error -> {});

        tokenStream.start();
        assertThat(firstRun.await(5, SECONDS)).isTrue();
        tokenStream.start();

        assertThat(bothRuns.await(5, SECONDS)).isTrue();
        assertThat(events(OutputGuardrailExecutedEvent.class))
                .extracting(event -> event.invocationContext().invocationId())
                .containsExactlyElementsOf(events(AiServiceStartedEvent.class).stream()
                        .map(event -> event.invocationContext().invocationId())
                        .toList());
    }

    @Test
    void should_fire_error_event_when_the_reactive_stream_fails_on_its_own() throws Exception {
        ReactiveEventsAssistant assistant = AiServices.builder(ReactiveEventsAssistant.class)
                .streamingChatModel(StreamingChatModelMock.thatAlwaysStreams("a", "b", "c", "d", "e", "f", "g", "h"))
                .streamingBufferSize(2)
                .registerListeners(listeners())
                .registerListener((AiServiceInvocationSummaryListener) events::add)
                .build();
        CompletableFuture<Void> failed = new CompletableFuture<>();

        assistant.chat("Hello").subscribe(new Flow.Subscriber<>() {

            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(1);
            }

            @Override
            public void onNext(AiServiceStreamingEvent item) {}

            @Override
            public void onError(Throwable throwable) {
                failed.completeExceptionally(throwable);
            }

            @Override
            public void onComplete() {
                failed.complete(null);
            }
        });

        assertThat(failed).failsWithin(5, SECONDS);
        assertSingleTerminalEvent(AiServiceErrorEvent.class);
        assertThat(events(AiServiceInvocationSummaryEvent.class))
                .singleElement()
                .satisfies(summary -> assertThat(summary.events()).last().isInstanceOf(AiServiceErrorEvent.class));
    }

    @Test
    void should_fire_error_event_when_async_invocation_fails_with_a_cancellation_exception() throws Exception {
        AsyncAssistant assistant = AiServices.builder(AsyncAssistant.class)
                .chatModel(new ChatModelMock(new CancellationException("the model call was cancelled")))
                .registerListeners(listeners())
                .build();

        CompletableFuture<String> future = assistant.chat("Hello");

        assertThat(future).failsWithin(5, SECONDS);
        assertThat(future.isCancelled()).isFalse();
        assertSingleTerminalEvent(AiServiceErrorEvent.class);
    }

    @Test
    void should_report_every_tool_of_a_concurrent_round_of_a_token_stream_when_a_tool_throws_an_error() {
        StreamingAssistant assistant = AiServices.builder(StreamingAssistant.class)
                .streamingChatModel(StreamingChatModelMock.thatAlwaysStreams(
                        callingTools("succeed", "explode"), AiMessage.from("answer")))
                .tools(Map.of(
                        ToolSpecification.builder().name("succeed").build(),
                        (ToolExecutor) (request, memoryId) -> "ok",
                        ToolSpecification.builder().name("explode").build(),
                        (ToolExecutor) (request, memoryId) -> {
                            throw new AssertionError("boom");
                        }))
                .executeToolsConcurrently()
                .registerListeners(listeners())
                .build();

        assertThat(start(assistant.chat("Hello")))
                .failsWithin(5, SECONDS)
                .withThrowableThat()
                .havingRootCause()
                .isInstanceOf(AssertionError.class)
                .withMessage("boom");

        assertThat(events(ToolExecutedEvent.class))
                .extracting(event -> event.request().name(), ToolExecutedEvent::isError)
                .containsExactlyInAnyOrder(tuple("succeed", false), tuple("explode", true));
    }

    @Test
    void should_report_a_failed_tool_execution_of_a_token_stream_with_the_default_error_handler() {
        StreamingAssistant assistant = AiServices.builder(StreamingAssistant.class)
                .streamingChatModel(
                        StreamingChatModelMock.thatAlwaysStreams(callingTools("fail"), AiMessage.from("answer")))
                .tools(new Tools())
                .registerListeners(listeners())
                .build();

        assertThat(start(assistant.chat("Hello"))).succeedsWithin(5, SECONDS);

        assertFailedToolExecutionReported();
    }

    @Test
    void should_rethrow_virtual_machine_error_of_a_concurrent_tool_untouched() {
        StackOverflowError overflow = new StackOverflowError("too deep");
        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(ChatModelMock.thatAlwaysResponds(
                        callingTools("succeed", "overflow"), AiMessage.from("answer")))
                .tools(succeedingAndThrowingTools("overflow", overflow))
                .executeToolsConcurrently()
                .registerListeners(listeners())
                .build();

        assertThatThrownBy(() -> assistant.chat("Hello")).isSameAs(overflow);

        assertThat(events(ToolExecutedEvent.class)).isEmpty();
    }

    @Test
    void should_rethrow_virtual_machine_error_of_a_concurrent_tool_of_a_token_stream_untouched() {
        StackOverflowError overflow = new StackOverflowError("too deep");
        StreamingAssistant assistant = AiServices.builder(StreamingAssistant.class)
                .streamingChatModel(StreamingChatModelMock.thatAlwaysStreams(
                        callingTools("succeed", "overflow"), AiMessage.from("answer")))
                .tools(succeedingAndThrowingTools("overflow", overflow))
                .executeToolsConcurrently()
                .registerListeners(listeners())
                .build();

        assertThat(start(assistant.chat("Hello")))
                .failsWithin(5, SECONDS)
                .withThrowableThat()
                .havingCause()
                .isSameAs(overflow);

        assertThat(events(ToolExecutedEvent.class)).isEmpty();
    }

    @Test
    void should_report_every_tool_of_a_sequential_round_when_a_tool_throws_an_error() {
        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(ChatModelMock.thatAlwaysResponds(
                        callingTools("succeed", "explode"), AiMessage.from("answer")))
                .tools(succeedingAndThrowingTools("explode", new AssertionError("boom")))
                .registerListeners(listeners())
                .build();

        assertThatThrownBy(() -> assistant.chat("Hello"))
                .isExactlyInstanceOf(AssertionError.class)
                .hasMessage("boom");

        assertThat(events(ToolExecutedEvent.class))
                .extracting(event -> event.request().name(), ToolExecutedEvent::isError)
                .containsExactly(tuple("succeed", false), tuple("explode", true));
    }

    @Test
    void should_report_every_tool_of_a_sequential_round_of_a_token_stream_when_a_tool_throws_an_error() {
        StreamingAssistant assistant = AiServices.builder(StreamingAssistant.class)
                .streamingChatModel(StreamingChatModelMock.thatAlwaysStreams(
                        callingTools("succeed", "explode"), AiMessage.from("answer")))
                .tools(succeedingAndThrowingTools("explode", new AssertionError("boom")))
                .registerListeners(listeners())
                .build();

        assertThat(start(assistant.chat("Hello")))
                .failsWithin(5, SECONDS)
                .withThrowableThat()
                .havingRootCause()
                .isInstanceOf(AssertionError.class)
                .withMessage("boom");

        assertThat(events(ToolExecutedEvent.class))
                .extracting(event -> event.request().name(), ToolExecutedEvent::isError)
                .containsExactly(tuple("succeed", false), tuple("explode", true));
    }

    @Test
    void should_rethrow_virtual_machine_error_without_firing_an_error_event() {
        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(ChatModelMock.thatResponds(request -> {
                    throw new StackOverflowError("too deep");
                }))
                .registerListeners(listeners())
                .build();

        assertThatThrownBy(() -> assistant.chat("Hello"))
                .isExactlyInstanceOf(StackOverflowError.class)
                .hasMessage("too deep");

        assertThat(events(AiServiceErrorEvent.class)).isEmpty();
    }

    private void assertRetrievedSource() {
        assertThat(events(RetrievalAugmentedEvent.class)).singleElement().satisfies(event -> {
            assertThat(event.chatMessage()).isEqualTo(UserMessage.from("Hello"));
            assertThat(event.contents())
                    .extracting(content -> content.textSegment().text())
                    .containsExactly("source");
        });
    }

    private void assertToolExecutionsReported(boolean inRequestOrder) {
        var executions = assertThat(events(ToolExecutedEvent.class))
                .extracting(event -> event.request().name(), ToolExecutedEvent::isError);
        if (inRequestOrder) {
            executions.containsExactly(
                    tuple("succeed", false),
                    tuple("fail", true));
        } else {
            executions.containsExactlyInAnyOrder(
                    tuple("succeed", false),
                    tuple("fail", true));
        }
    }

    private static Map<ToolSpecification, ToolExecutor> succeedingAndThrowingTools(String throwingTool, Error error) {
        return Map.of(
                ToolSpecification.builder().name("succeed").build(),
                (ToolExecutor) (request, memoryId) -> "ok",
                ToolSpecification.builder().name(throwingTool).build(),
                (ToolExecutor) (request, memoryId) -> {
                    throw error;
                });
    }

    private static AiMessage callingTools(String... toolNames) {
        return AiMessage.from(Arrays.stream(toolNames)
                .map(name -> ToolExecutionRequest.builder()
                        .id(name)
                        .name(name)
                        .arguments("{}")
                        .build())
                .toList());
    }

    private static CompletableFuture<ChatResponse> start(TokenStream tokenStream) {
        CompletableFuture<ChatResponse> done = new CompletableFuture<>();
        tokenStream
                .onPartialResponse(partialResponse -> {})
                .onCompleteResponse(done::complete)
                .onError(done::completeExceptionally)
                .start();
        return done;
    }

    private void assertFailedToolExecutionReported() {
        assertFailedToolExecutionReported("fail", "tool failed");
    }

    private void assertFailedToolExecutionReported(String toolName, String resultText) {
        assertThat(events(ToolExecutedEvent.class)).singleElement().satisfies(event -> {
            assertThat(event.request().name()).isEqualTo(toolName);
            assertThat(event.isError()).isTrue();
            assertThat(event.resultText()).contains(resultText);
        });
    }

    private static <T> CompletableFuture<Void> consume(Flow.Publisher<T> publisher) {
        CompletableFuture<Void> done = new CompletableFuture<>();
        publisher.subscribe(new Flow.Subscriber<>() {

            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(T item) {}

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

    private static InputGuardrail blockingGuardrail() {
        return new InputGuardrail() {
            @Override
            public InputGuardrailResult validate(UserMessage userMessage) {
                return failure("blocked");
            }

            @Override
            public CompletableFuture<InputGuardrailResult> validateAsync(InputGuardrailRequest request) {
                return CompletableFuture.completedFuture(validate(request));
            }
        };
    }

    @Test
    void should_compensate_the_round_when_the_hallucinated_tool_name_strategy_returns_an_error() {
        BookingTools tools = new BookingTools();
        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(ChatModelMock.thatResponds(callToolsThenAnswer("book", "unknown")))
                .tools(tools)
                .compensateOnToolErrors(true)
                .hallucinatedToolNameStrategy(request -> ToolExecutionResultMessage.builder()
                        .id(request.id())
                        .toolName(request.name())
                        .text("There is no such tool")
                        .isError(true)
                        .build())
                .registerListeners(listeners())
                .build();

        assertThat(assistant.chat("Hello")).isEqualTo("answer");

        assertThat(tools.booked.get()).isEqualTo(1);
        assertThat(tools.cancelled.get()).isEqualTo(1);
    }

    @Test
    void should_still_fail_the_invocation_when_the_model_calls_an_unknown_tool_with_the_default_strategy()
            throws Exception {
        BookingTools tools = new BookingTools();
        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(ChatModelMock.thatResponds(callToolsThenAnswer("book", "unknown")))
                .tools(tools)
                .compensateOnToolErrors(true)
                .registerListeners(listeners())
                .build();

        assertThatThrownBy(() -> assistant.chat("Hello"))
                .isExactlyInstanceOf(RuntimeException.class)
                .hasMessage("The LLM is trying to execute the 'unknown' tool, but no such tool exists. Most likely, it "
                        + "is a hallucination. You can override this default strategy by setting the "
                        + "hallucinatedToolNameStrategy on the AiService");

        assertSingleTerminalEvent(AiServiceErrorEvent.class);
    }

    static class BookingTools {

        final AtomicInteger booked = new AtomicInteger();
        final AtomicInteger cancelled = new AtomicInteger();

        @Tool
        String book() {
            booked.incrementAndGet();
            return "booked";
        }

        @CompensateFor("book")
        void cancelBooking() {
            cancelled.incrementAndGet();
        }
    }

    static class Tools {

        @Tool
        String succeed() {
            return "ok";
        }

        @Tool
        String fail() {
            throw new IllegalStateException("tool failed");
        }

        @Tool
        String echo(String text) {
            return text;
        }
    }

    private static Function<ChatRequest, AiMessage> callToolsThenAnswer(String... toolNames) {
        return request -> {
            List<ChatMessage> messages = request.messages();
            if (messages.get(messages.size() - 1) instanceof ToolExecutionResultMessage) {
                return AiMessage.from("answer");
            }
            List<ToolExecutionRequest> toolExecutionRequests = Arrays.stream(toolNames)
                    .map(name -> ToolExecutionRequest.builder()
                            .id(name)
                            .name(name)
                            .arguments("{}")
                            .build())
                    .toList();
            return AiMessage.from(toolExecutionRequests);
        };
    }

    private static RetrievalAugmentor offloading(ContentRetriever contentRetriever) {
        return DefaultRetrievalAugmentor.builder()
                .contentRetriever(contentRetriever)
                .offloadBlocking(true)
                .build();
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(5, SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private <T extends AiServiceEvent> List<T> events(Class<T> eventClass) {
        return events.stream().filter(eventClass::isInstance).map(eventClass::cast).toList();
    }

    private List<AiServiceListener<?>> listeners() {
        return List.of(
                listener(AiServiceStartedEvent.class),
                listener(AiServiceRequestIssuedEvent.class),
                listener(AiServiceResponseReceivedEvent.class),
                listener(ToolExecutedEvent.class),
                listener(RetrievalAugmentedEvent.class),
                listener(AiServiceCompletedEvent.class),
                listener(AiServiceErrorEvent.class),
                listener(AiServiceCancelledEvent.class));
    }

    private <T extends AiServiceEvent> AiServiceListener<T> listener(Class<T> eventClass) {
        return new AiServiceListener<>() {

            @Override
            public Class<T> getEventClass() {
                return eventClass;
            }

            @Override
            public void onEvent(T event) {
                events.add(event);
                if (event instanceof AiServiceCompletedEvent
                        || event instanceof AiServiceErrorEvent
                        || event instanceof AiServiceCancelledEvent) {
                    finished.countDown();
                    twoTerminalEvents.countDown();
                    onTerminalEvent.accept(event);
                }
            }
        };
    }

    private void assertSingleTerminalEvent(Class<? extends AiServiceEvent> expected) throws InterruptedException {
        assertThat(finished.await(5, SECONDS)).isTrue();
        assertThat(twoTerminalEvents.await(150, MILLISECONDS))
                .as("a second terminal event was fired: %s", terminalEvents())
                .isFalse();
        assertThat(terminalEvents()).singleElement().isInstanceOf(expected);
    }

    private List<AiServiceEvent> terminalEvents() {
        return events.stream()
                .filter(event -> event instanceof AiServiceCompletedEvent
                        || event instanceof AiServiceErrorEvent
                        || event instanceof AiServiceCancelledEvent)
                .toList();
    }
}
