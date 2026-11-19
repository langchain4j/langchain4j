package dev.langchain4j.service.tool;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.guardrail.GuardrailException;
import dev.langchain4j.guardrail.GuardrailRequestParams;
import dev.langchain4j.guardrail.ToolInputGuardrail;
import dev.langchain4j.guardrail.ToolInputGuardrailException;
import dev.langchain4j.guardrail.ToolInputGuardrailRequest;
import dev.langchain4j.guardrail.ToolInputGuardrailResult;
import dev.langchain4j.guardrail.ToolOutputGuardrail;
import dev.langchain4j.guardrail.ToolOutputGuardrailException;
import dev.langchain4j.guardrail.ToolOutputGuardrailRequest;
import dev.langchain4j.guardrail.ToolOutputGuardrailResult;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.observability.api.event.ToolExecutedEvent;
import dev.langchain4j.observability.api.event.ToolInputGuardrailExecutedEvent;
import dev.langchain4j.observability.api.listener.ToolExecutedEventListener;
import dev.langchain4j.observability.api.listener.ToolInputGuardrailExecutedListener;
import dev.langchain4j.service.AiServiceStreamingEvent;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.test.mock.MockChatModel;
import dev.langchain4j.test.mock.MockStreamingChatModel;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Tool guardrails behave in the non-blocking AI service modes as in the blocking one ({@link ToolGuardrailExecutionTest}):
 * the {@code CompletableFuture} mode, {@code TokenStream} (with tools executed sequentially or concurrently) and the
 * reactive {@code Flow.Publisher} mode. A fatal failure fails the future or goes to {@code onError}.
 */
class ToolGuardrailNonBlockingExecutionTest {

    private static final String ORIGINAL_ARGUMENTS = "{\"q\":\"a\"}";
    private static final String REWRITTEN_ARGUMENTS = "{\"q\":\"b\"}";
    private static final String MALFORMED_ARGUMENTS = "{\"q\":";
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final ToolSpecification SEARCH =
            ToolSpecification.builder().name("search").description("Searches").build();
    // the result factories are default methods of the interfaces, which lambdas cannot call
    private static final ToolInputGuardrail INPUT = request -> null;
    private static final ToolOutputGuardrail OUTPUT = request -> null;

    enum Mode {
        COMPLETABLE_FUTURE,
        TOKEN_STREAM,
        TOKEN_STREAM_CONCURRENT,
        REACTIVE
    }

    interface FutureAssistant {

        CompletableFuture<String> chat(@UserMessage String message);
    }

    interface StreamingAssistant {

        TokenStream chat(@UserMessage String message);
    }

    interface ReactiveAssistant {

        Flow.Publisher<AiServiceStreamingEvent> chat(@UserMessage String message);
    }

    /** A tool that records the arguments it ran with. */
    static final class RecordingTool implements ToolExecutor {

        final List<String> receivedArguments = new CopyOnWriteArrayList<>();

        @Override
        public String execute(ToolExecutionRequest request, Object memoryId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ToolExecutionResult executeWithContext(ToolExecutionRequest request, InvocationContext context) {
            receivedArguments.add(request.arguments());
            return ToolExecutionResult.builder()
                    .resultText("found " + request.arguments())
                    .build();
        }

        @Override
        public CompletableFuture<ToolExecutionResult> executeAsync(
                ToolExecutionRequest request, InvocationContext context) {
            return CompletableFuture.completedFuture(executeWithContext(request, context));
        }
    }

    /** Everything a run of the AI service reports. */
    private static final class Run {

        final RecordingTool tool = new RecordingTool();
        final ChatMemory memory = MessageWindowChatMemory.withMaxMessages(20);
        final List<ToolExecution> afterToolExecutions = new CopyOnWriteArrayList<>();
        final List<ToolExecutedEvent> toolEvents = new CopyOnWriteArrayList<>();
        final List<ToolInputGuardrailExecutedEvent> inputGuardrailEvents = new CopyOnWriteArrayList<>();
        final AtomicInteger errorHandlerCalls = new AtomicInteger();
        final CompletableFuture<String> answer = new CompletableFuture<>();
        Supplier<List<ChatRequest>> requests;

        /** The tool result message the LLM got in its second request. */
        ToolExecutionResultMessage toolResultMessageOfSecondRequest() {
            return requests.get().get(1).messages().stream()
                    .filter(ToolExecutionResultMessage.class::isInstance)
                    .map(ToolExecutionResultMessage.class::cast)
                    .findFirst()
                    .orElseThrow();
        }

        /** The tool calls of the AI message the LLM got back in its second request. */
        List<ToolExecutionRequest> toolCallsOfSecondRequest() {
            return toolCalls(requests.get().get(1).messages().stream());
        }

        /** The tool calls of the AI message that chat memory holds. */
        List<ToolExecutionRequest> toolCallsInMemory() {
            return toolCalls(memory.messages().stream());
        }

        private static List<ToolExecutionRequest> toolCalls(
                java.util.stream.Stream<dev.langchain4j.data.message.ChatMessage> messages) {
            return messages.filter(AiMessage.class::isInstance)
                    .map(AiMessage.class::cast)
                    .filter(AiMessage::hasToolExecutionRequests)
                    .flatMap(message -> message.toolExecutionRequests().stream())
                    .toList();
        }

        /** The answer, or the root cause of the failure of the invocation. */
        Throwable failure() {
            assertThat(answer).failsWithin(TIMEOUT);
            return answer.handle((ignored, error) -> rootCause(error)).join();
        }

        private static Throwable rootCause(Throwable error) {
            var cause = error;
            while ((cause.getCause() != null) && (cause.getCause() != cause)) {
                cause = cause.getCause();
            }
            return cause;
        }
    }

    private static ToolExecutionRequest searchCall(String id) {
        return ToolExecutionRequest.builder()
                .id(id)
                .name("search")
                .arguments(ORIGINAL_ARGUMENTS)
                .build();
    }

    private static List<AiMessage> responsesCallingSearch() {
        return List.of(AiMessage.from(searchCall("1")), AiMessage.from("done"));
    }

    private static List<AiMessage> responsesCallingSearchWith(String arguments) {
        return List.of(
                AiMessage.from(searchCall("1").toBuilder().arguments(arguments).build()), AiMessage.from("done"));
    }

    private static Run run(Mode mode, AiServiceTool.Builder toolBuilder) {
        return run(mode, toolBuilder, responsesCallingSearch(), services -> {});
    }

    private static Run run(
            Mode mode,
            AiServiceTool.Builder toolBuilder,
            List<AiMessage> responses,
            Consumer<AiServices<?>> configuration) {
        var run = new Run();
        var tool = toolBuilder.toolSpecification(SEARCH).toolExecutor(run.tool).build();

        switch (mode) {
            case COMPLETABLE_FUTURE -> {
                var model = MockChatModel.thatResponds(responses);
                run.requests = model::requests;
                var services =
                        configure(AiServices.builder(FutureAssistant.class).chatModel(model), run, tool);
                configuration.accept(services);
                services.build().chat("hello").whenComplete((answer, error) -> complete(run, answer, error));
            }
            case TOKEN_STREAM, TOKEN_STREAM_CONCURRENT -> {
                var model = MockStreamingChatModel.thatResponds(responses);
                run.requests = model::requests;
                var services =
                        configure(AiServices.builder(StreamingAssistant.class).streamingChatModel(model), run, tool);
                if (mode == Mode.TOKEN_STREAM_CONCURRENT) {
                    services.executeToolsConcurrently();
                }
                configuration.accept(services);
                services.build()
                        .chat("hello")
                        .onPartialResponse(ignored -> {})
                        .onCompleteResponse(response ->
                                run.answer.complete(response.aiMessage().text()))
                        .onError(run.answer::completeExceptionally)
                        .start();
            }
            case REACTIVE -> {
                var model = MockStreamingChatModel.thatResponds(responses);
                run.requests = model::requests;
                var services =
                        configure(AiServices.builder(ReactiveAssistant.class).streamingChatModel(model), run, tool);
                configuration.accept(services);
                services.build().chat("hello").subscribe(new AnswerSubscriber(run.answer));
            }
        }

        // wait for the invocation to end, normally or not
        run.answer
                .handle((ignored, ignoredError) -> null)
                .orTimeout(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)
                .join();
        return run;
    }

    private static void complete(Run run, String answer, Throwable error) {
        if (error != null) {
            run.answer.completeExceptionally(error);
        } else {
            run.answer.complete(answer);
        }
    }

    private static <T> AiServices<T> configure(AiServices<T> services, Run run, AiServiceTool tool) {
        return services.chatMemory(run.memory)
                .tools(List.<AiServiceTool>of(tool))
                .afterToolExecution(run.afterToolExecutions::add)
                .toolExecutionErrorHandler((error, context) -> {
                    run.errorHandlerCalls.incrementAndGet();
                    return ToolErrorHandlerResult.text("handled");
                })
                .registerListener((ToolExecutedEventListener) run.toolEvents::add)
                .registerListener((ToolInputGuardrailExecutedListener) run.inputGuardrailEvents::add);
    }

    /** Completes {@code answer} with the final response of a reactive invocation, or with its failure. */
    private static final class AnswerSubscriber implements Flow.Subscriber<AiServiceStreamingEvent> {

        private final CompletableFuture<String> answer;

        AnswerSubscriber(CompletableFuture<String> answer) {
            this.answer = answer;
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            subscription.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(AiServiceStreamingEvent event) {
            if (event instanceof AiServiceStreamingEvent.FinalResponseEvent finalResponse) {
                answer.complete(finalResponse.chatResponse().aiMessage().text());
            }
        }

        @Override
        public void onError(Throwable error) {
            answer.completeExceptionally(error);
        }

        @Override
        public void onComplete() {
            answer.complete(null);
        }
    }

    /**
     * An input guardrail that validates asynchronously, as the {@code CompletableFuture} and reactive modes require,
     * and in a blocking way for {@code TokenStream}.
     */
    private static ToolInputGuardrail input(Function<ToolInputGuardrailRequest, ToolInputGuardrailResult> validation) {
        return new ToolInputGuardrail() {
            @Override
            public ToolInputGuardrailResult validate(ToolInputGuardrailRequest request) {
                return validation.apply(request);
            }

            @Override
            public CompletableFuture<ToolInputGuardrailResult> validateAsync(ToolInputGuardrailRequest request) {
                return CompletableFuture.completedFuture(validate(request));
            }
        };
    }

    /** The output counterpart of {@link #input(Function)}. */
    private static ToolOutputGuardrail output(
            Function<ToolOutputGuardrailRequest, ToolOutputGuardrailResult> validation) {
        return new ToolOutputGuardrail() {
            @Override
            public ToolOutputGuardrailResult validate(ToolOutputGuardrailRequest request) {
                return validation.apply(request);
            }

            @Override
            public CompletableFuture<ToolOutputGuardrailResult> validateAsync(ToolOutputGuardrailRequest request) {
                return CompletableFuture.completedFuture(validate(request));
            }
        };
    }

    private static ToolInputGuardrail rewriteInput() {
        return input(request -> ToolInputGuardrailResult.successWith(REWRITTEN_ARGUMENTS));
    }

    // ---------- parity with the blocking mode ----------

    @ParameterizedTest
    @EnumSource(Mode.class)
    void a_tool_without_guardrails_runs_as_before(Mode mode) {
        var run = run(mode, AiServiceTool.builder());

        assertThat(run.answer).isCompletedWithValue("done");
        assertThat(run.tool.receivedArguments).containsExactly(ORIGINAL_ARGUMENTS);
        assertThat(run.afterToolExecutions)
                .singleElement()
                .returns(ORIGINAL_ARGUMENTS, execution -> execution.request().arguments())
                .returns("found " + ORIGINAL_ARGUMENTS, ToolExecution::result);
        assertThat(run.inputGuardrailEvents).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(Mode.class)
    void an_input_rewrite_is_what_the_tool_runs_with_and_what_everything_downstream_carries(Mode mode) {
        var run = run(mode, AiServiceTool.builder().toolInputGuardrails(rewriteInput()));

        assertThat(run.answer).isCompletedWithValue("done");
        assertThat(run.tool.receivedArguments).containsExactly(REWRITTEN_ARGUMENTS);
        assertThat(run.afterToolExecutions)
                .singleElement()
                .returns(REWRITTEN_ARGUMENTS, execution -> execution.request().arguments())
                .returns("found " + REWRITTEN_ARGUMENTS, ToolExecution::result);
        assertThat(run.toolEvents)
                .singleElement()
                .returns(REWRITTEN_ARGUMENTS, event -> event.request().arguments());
        assertThat(run.toolCallsInMemory())
                .singleElement()
                .returns(REWRITTEN_ARGUMENTS, ToolExecutionRequest::arguments);
        assertThat(run.toolCallsOfSecondRequest())
                .singleElement()
                .returns(REWRITTEN_ARGUMENTS, ToolExecutionRequest::arguments);
        assertThat(run.toolResultMessageOfSecondRequest().text()).isEqualTo("found " + REWRITTEN_ARGUMENTS);
    }

    @ParameterizedTest
    @EnumSource(Mode.class)
    void an_output_rewrite_is_what_the_llm_sees(Mode mode) {
        var run = run(
                mode,
                AiServiceTool.builder()
                        .toolOutputGuardrails(output(request -> ToolOutputGuardrailResult.successWith("clean"))));

        assertThat(run.toolResultMessageOfSecondRequest().text()).isEqualTo("clean");
        assertThat(run.afterToolExecutions).singleElement().returns("clean", ToolExecution::result);
    }

    @ParameterizedTest
    @EnumSource(Mode.class)
    void a_non_fatal_input_failure_does_not_run_the_tool_and_reports_the_message_verbatim(Mode mode) {
        ToolInputGuardrail failing = input(request -> INPUT.failure("not allowed"));

        var run = run(mode, AiServiceTool.builder().toolInputGuardrails(failing));

        assertThat(run.answer).isCompletedWithValue("done");
        assertThat(run.tool.receivedArguments).isEmpty();
        assertThat(run.toolResultMessageOfSecondRequest())
                .returns("not allowed", ToolExecutionResultMessage::text)
                .returns(true, ToolExecutionResultMessage::isError);
    }

    @ParameterizedTest
    @EnumSource(Mode.class)
    void arguments_an_input_guardrail_cannot_parse_go_to_the_arguments_error_handler(Mode mode) {
        ToolInputGuardrail parsing = input(request -> {
            request.argumentsAsMap();
            return ToolInputGuardrailResult.success();
        });
        var argumentsErrors = new CopyOnWriteArrayList<Throwable>();

        var run = run(
                mode,
                AiServiceTool.builder().toolInputGuardrails(parsing),
                responsesCallingSearchWith(MALFORMED_ARGUMENTS),
                services -> services.toolArgumentsErrorHandler((error, context) -> {
                    argumentsErrors.add(error);
                    return ToolErrorHandlerResult.text("fix the arguments");
                }));

        assertThat(run.answer).isCompletedWithValue("done");
        assertThat(run.tool.receivedArguments).isEmpty();
        assertThat(argumentsErrors).hasSize(1);
        assertThat(run.errorHandlerCalls).hasValue(0);
        assertThat(run.toolResultMessageOfSecondRequest())
                .returns("fix the arguments", ToolExecutionResultMessage::text)
                .returns(true, ToolExecutionResultMessage::isError);
    }

    @ParameterizedTest
    @EnumSource(Mode.class)
    void arguments_an_input_guardrail_cannot_parse_fail_the_invocation_as_the_arguments_error_handler_decides(
            Mode mode) {
        ToolInputGuardrail parsing = input(request -> {
            request.argumentsAsMap();
            return ToolInputGuardrailResult.success();
        });

        var run = run(
                mode,
                AiServiceTool.builder().toolInputGuardrails(parsing),
                responsesCallingSearchWith(MALFORMED_ARGUMENTS),
                services -> services.toolArgumentsErrorHandler(ToolArgumentsErrorHandler.failInvocation()));

        assertThat(run.failure()).isNotInstanceOf(GuardrailException.class);
        assertThat(run.tool.receivedArguments).isEmpty();
        assertThat(run.errorHandlerCalls).hasValue(0);
        assertThat(run.requests.get()).hasSize(1);
    }

    @ParameterizedTest
    @EnumSource(Mode.class)
    void a_fatal_input_failure_ends_the_invocation_past_the_tool_execution_error_handler(Mode mode) {
        ToolInputGuardrail fatal = input(request -> INPUT.fatal("stop"));

        var run =
                run(mode, AiServiceTool.builder().toolInputGuardrails(fatal), responsesCallingSearch(), services -> {});

        assertThat(run.failure()).isInstanceOf(ToolInputGuardrailException.class);
        assertThat(run.tool.receivedArguments).isEmpty();
        assertThat(run.errorHandlerCalls).hasValue(0);
        assertThat(run.requests.get()).hasSize(1);
    }

    @ParameterizedTest
    @EnumSource(Mode.class)
    void a_fatal_output_failure_after_an_input_rewrite_ends_the_invocation_and_reports_the_rewritten_request(
            Mode mode) {
        ToolOutputGuardrail fatal = output(request -> OUTPUT.fatal("stop"));

        var run = run(
                mode,
                AiServiceTool.builder().toolInputGuardrails(rewriteInput()).toolOutputGuardrails(fatal),
                responsesCallingSearch(),
                services -> {});

        assertThat(run.failure()).isInstanceOf(ToolOutputGuardrailException.class);
        assertThat(run.tool.receivedArguments).containsExactly(REWRITTEN_ARGUMENTS);
        assertThat(run.errorHandlerCalls).hasValue(0);
        assertThat(run.toolEvents)
                .singleElement()
                .returns(REWRITTEN_ARGUMENTS, event -> event.request().arguments())
                .returns(true, ToolExecutedEvent::isError);
        assertThat(run.afterToolExecutions)
                .allSatisfy(
                        execution -> assertThat(execution.request().arguments()).isEqualTo(REWRITTEN_ARGUMENTS));
    }

    @ParameterizedTest
    @EnumSource(Mode.class)
    void guardrails_that_validate_on_another_thread_are_applied(Mode mode) {
        var asyncRewrite = new ToolInputGuardrail() {
            @Override
            public ToolInputGuardrailResult validate(ToolInputGuardrailRequest request) {
                return ToolInputGuardrailResult.successWith(REWRITTEN_ARGUMENTS);
            }

            @Override
            public CompletableFuture<ToolInputGuardrailResult> validateAsync(ToolInputGuardrailRequest request) {
                return CompletableFuture.supplyAsync(
                        () -> validate(request), CompletableFuture.delayedExecutor(20, TimeUnit.MILLISECONDS));
            }
        };

        var run = run(mode, AiServiceTool.builder().toolInputGuardrails(asyncRewrite));

        assertThat(run.answer).isCompletedWithValue("done");
        assertThat(run.tool.receivedArguments).containsExactly(REWRITTEN_ARGUMENTS);
    }

    @ParameterizedTest
    @EnumSource(Mode.class)
    void guardrails_get_the_parameters_of_the_invocation_and_their_events_reach_its_listeners(Mode mode) {
        var params = new CopyOnWriteArrayList<GuardrailRequestParams>();
        ToolInputGuardrail recording = input(request -> {
            params.add(request.requestParams());
            return ToolInputGuardrailResult.success();
        });

        var run = run(mode, AiServiceTool.builder().toolInputGuardrails(recording));

        assertThat(params).singleElement().returns("hello", GuardrailRequestParams::userMessageTemplate);
        assertThat(run.inputGuardrailEvents)
                .singleElement()
                .returns(
                        params.get(0).invocationContext().invocationId(),
                        event -> event.invocationContext().invocationId());
    }

    @ParameterizedTest
    @EnumSource(Mode.class)
    void guardrails_are_not_applied_to_a_hallucinated_tool_name(Mode mode) {
        var inputCalls = new AtomicInteger();
        ToolInputGuardrail counting = input(request -> {
            inputCalls.incrementAndGet();
            return ToolInputGuardrailResult.success();
        });
        var unknownCall = ToolExecutionRequest.builder()
                .id("1")
                .name("unknown")
                .arguments("{}")
                .build();

        var run = run(
                mode,
                AiServiceTool.builder(),
                List.of(AiMessage.from(unknownCall), AiMessage.from("done")),
                services -> services.hallucinatedToolNameStrategy(
                                request -> ToolExecutionResultMessage.from(request, "no such tool"))
                        .toolInputGuardrails(counting));

        assertThat(run.answer).isCompletedWithValue("done");
        assertThat(inputCalls).hasValue(0);
    }

    @ParameterizedTest
    @EnumSource(Mode.class)
    void guardrails_apply_to_every_call_of_a_round(Mode mode) {
        var run = run(
                mode,
                AiServiceTool.builder().toolInputGuardrails(rewriteInput()),
                List.of(AiMessage.from(searchCall("1"), searchCall("2")), AiMessage.from("done")),
                services -> {});

        assertThat(run.tool.receivedArguments).containsExactly(REWRITTEN_ARGUMENTS, REWRITTEN_ARGUMENTS);
        assertThat(run.toolCallsInMemory())
                .extracting(ToolExecutionRequest::id, ToolExecutionRequest::arguments)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("1", REWRITTEN_ARGUMENTS),
                        org.assertj.core.groups.Tuple.tuple("2", REWRITTEN_ARGUMENTS));
    }

    // ---------- cancellation ----------

    /** An input guardrail whose validation stays in flight until it is canceled. */
    private static final class PendingInputGuardrail implements ToolInputGuardrail {

        final List<CompletableFuture<ToolInputGuardrailResult>> pending = new CopyOnWriteArrayList<>();
        final CountDownLatch entered = new CountDownLatch(1);

        @Override
        public ToolInputGuardrailResult validate(ToolInputGuardrailRequest request) {
            throw new UnsupportedOperationException("validated asynchronously only");
        }

        @Override
        public CompletableFuture<ToolInputGuardrailResult> validateAsync(ToolInputGuardrailRequest request) {
            var future = new CompletableFuture<ToolInputGuardrailResult>();
            pending.add(future);
            entered.countDown();
            return future;
        }
    }

    @Test
    void canceling_a_completable_future_invocation_cancels_the_guardrails_in_flight_and_runs_no_tool()
            throws InterruptedException {
        var guardrail = new PendingInputGuardrail();
        var tool = new RecordingTool();
        var model = MockChatModel.thatResponds(AiMessage.from(searchCall("1")), AiMessage.from("done"));
        var assistant = AiServices.builder(FutureAssistant.class)
                .chatModel(model)
                .tools(List.<AiServiceTool>of(AiServiceTool.builder()
                        .toolSpecification(SEARCH)
                        .toolExecutor(tool)
                        .toolInputGuardrails(guardrail)
                        .build()))
                .build();

        var answer = assistant.chat("hello");
        assertThat(guardrail.entered.await(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS))
                .isTrue();
        answer.cancel(true);

        assertThat(answer).isCancelled();
        assertThat(guardrail.pending)
                .singleElement()
                .satisfies(future -> assertThat(future)
                        .failsWithin(TIMEOUT)
                        .withThrowableThat()
                        .isInstanceOf(java.util.concurrent.CancellationException.class));
        assertThat(tool.receivedArguments).isEmpty();
        assertThat(model.requests()).hasSize(1);
    }

    @Test
    void canceling_a_reactive_invocation_cancels_the_guardrails_in_flight_and_runs_no_tool()
            throws InterruptedException {
        var guardrail = new PendingInputGuardrail();
        var tool = new RecordingTool();
        var model = MockStreamingChatModel.thatResponds(AiMessage.from(searchCall("1")), AiMessage.from("done"));
        var assistant = AiServices.builder(ReactiveAssistant.class)
                .streamingChatModel(model)
                .tools(List.<AiServiceTool>of(AiServiceTool.builder()
                        .toolSpecification(SEARCH)
                        .toolExecutor(tool)
                        .toolInputGuardrails(guardrail)
                        .build()))
                .build();
        var subscription = new CompletableFuture<Flow.Subscription>();
        var terminated = new CountDownLatch(1);

        assistant.chat("hello").subscribe(new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription s) {
                subscription.complete(s);
                s.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(AiServiceStreamingEvent event) {}

            @Override
            public void onError(Throwable error) {
                terminated.countDown();
            }

            @Override
            public void onComplete() {
                terminated.countDown();
            }
        });
        assertThat(guardrail.entered.await(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS))
                .isTrue();
        subscription.join().cancel();

        assertThat(guardrail.pending)
                .singleElement()
                .satisfies(future -> assertThat(future)
                        .failsWithin(TIMEOUT)
                        .withThrowableThat()
                        .isInstanceOf(java.util.concurrent.CancellationException.class));
        assertThat(tool.receivedArguments).isEmpty();
        assertThat(model.requests()).hasSize(1);
    }
}
