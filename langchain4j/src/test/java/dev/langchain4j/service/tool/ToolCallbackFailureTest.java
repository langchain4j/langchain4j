package dev.langchain4j.service.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.type;

import dev.langchain4j.agent.tool.CompensateFor;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.guardrail.ToolInputGuardrail;
import dev.langchain4j.guardrail.ToolInputGuardrailRequest;
import dev.langchain4j.guardrail.ToolInputGuardrailResult;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.observability.api.event.AiServiceEvent;
import dev.langchain4j.observability.api.event.CompensationReason;
import dev.langchain4j.observability.api.event.ToolCompensatedEvent;
import dev.langchain4j.observability.api.event.ToolExecutedEvent;
import dev.langchain4j.observability.api.listener.ToolCompensatedEventListener;
import dev.langchain4j.observability.api.listener.ToolExecutedEventListener;
import dev.langchain4j.service.AiServiceStreamingEvent;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.test.mock.MockChatModel;
import dev.langchain4j.test.mock.MockStreamingChatModel;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * A callback of the tool execution ({@code beforeToolExecution} / {@code afterToolExecution}) that throws fails the
 * invocation and rolls back the compensable tools, like a tool that throws, with or without tool guardrails, in every
 * mode of the AI Service.
 */
class ToolCallbackFailureTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    enum Mode {
        SYNC,
        COMPLETABLE_FUTURE,
        TOKEN_STREAM,
        REACTIVE
    }

    interface SyncAssistant {

        String chat(@UserMessage String message);
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

    public static class Bank {

        final List<String> log = new CopyOnWriteArrayList<>();

        @Tool
        String credit(String name) {
            log.add("credit");
            return "credited";
        }

        @CompensateFor("credit")
        void uncredit(ToolExecution execution) {
            log.add("uncredit");
        }

        @Tool
        String debit(String name) {
            log.add("debit");
            return "debited";
        }

        @CompensateFor("debit")
        void undebit(ToolExecution execution) {
            log.add("undebit");
        }
    }

    // a guardrail that passes: the callbacks must not be mistaken for a failure of the guardrails
    private static final ToolInputGuardrail PASSING = new ToolInputGuardrail() {

        @Override
        public ToolInputGuardrailResult validate(ToolInputGuardrailRequest request) {
            return success();
        }

        @Override
        public CompletableFuture<ToolInputGuardrailResult> validateAsync(ToolInputGuardrailRequest request) {
            return CompletableFuture.completedFuture(success());
        }
    };

    static Stream<Arguments> modes() {
        return Stream.of(Mode.values())
                .flatMap(mode -> Stream.of(Arguments.of(mode, false), Arguments.of(mode, true)));
    }

    private static ToolExecutionRequest call(String id, String tool) {
        return ToolExecutionRequest.builder()
                .id(id)
                .name(tool)
                .arguments("{\"arg0\": \"Mario\"}")
                .build();
    }

    private record Run(Bank bank, List<AiServiceEvent> events, Throwable failure) {

        List<ToolCompensatedEvent> compensatedEvents() {
            return events.stream()
                    .filter(ToolCompensatedEvent.class::isInstance)
                    .map(ToolCompensatedEvent.class::cast)
                    .toList();
        }
    }

    private static Run run(Mode mode, boolean withGuardrails, boolean failBefore) {
        var bank = new Bank();
        var events = new CopyOnWriteArrayList<AiServiceEvent>();
        var responses = List.of(
                AiMessage.from(List.of(call("call-0", "credit"), call("call-1", "debit"))), AiMessage.from("done"));
        AiServices<?> builder = switch (mode) {
            case SYNC -> AiServices.builder(SyncAssistant.class).chatModel(MockChatModel.thatResponds(responses));
            case COMPLETABLE_FUTURE ->
                AiServices.builder(FutureAssistant.class).chatModel(MockChatModel.thatResponds(responses));
            case TOKEN_STREAM ->
                AiServices.builder(StreamingAssistant.class)
                        .streamingChatModel(MockStreamingChatModel.thatResponds(responses));
            case REACTIVE ->
                AiServices.builder(ReactiveAssistant.class)
                        .streamingChatModel(MockStreamingChatModel.thatResponds(responses));
        };
        builder.chatMemory(MessageWindowChatMemory.withMaxMessages(20))
                .tools(bank)
                .compensateOnToolErrors(true)
                .registerListener((ToolExecutedEventListener) events::add)
                .registerListener((ToolCompensatedEventListener) events::add);
        if (withGuardrails) {
            builder.toolInputGuardrails(PASSING);
        }
        if (failBefore) {
            builder.beforeToolExecution(before -> failFor(before.request()));
        } else {
            builder.afterToolExecution(after -> failFor(after.request()));
        }

        var answer = start(mode, builder.build());
        var failure = answer.handle((ignored, error) -> error)
                .orTimeout(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)
                .join();
        return new Run(bank, events, unwrap(failure));
    }

    private static CompletableFuture<String> start(Mode mode, Object assistant) {
        var answer = new CompletableFuture<String>();
        switch (mode) {
            case SYNC -> {
                try {
                    answer.complete(((SyncAssistant) assistant).chat("hello"));
                } catch (RuntimeException e) {
                    answer.completeExceptionally(e);
                }
            }
            case COMPLETABLE_FUTURE ->
                ((FutureAssistant) assistant).chat("hello").whenComplete((text, error) -> {
                    if (error != null) {
                        answer.completeExceptionally(error);
                    } else {
                        answer.complete(text);
                    }
                });
            case TOKEN_STREAM ->
                ((StreamingAssistant) assistant)
                        .chat("hello")
                        .onPartialResponse(ignored -> {})
                        .onCompleteResponse(response -> answer.complete(response.aiMessage().text()))
                        .onError(answer::completeExceptionally)
                        .start();
            case REACTIVE -> ((ReactiveAssistant) assistant).chat("hello").subscribe(new AnswerSubscriber(answer));
        }
        return answer;
    }

    private static Throwable unwrap(Throwable error) {
        return (error instanceof CompletionException) && (error.getCause() != null) ? error.getCause() : error;
    }

    private static void failFor(ToolExecutionRequest request) {
        if (request.name().equals("debit")) {
            throw new IllegalStateException("callback failed");
        }
    }

    @ParameterizedTest
    @MethodSource("modes")
    void an_after_callback_that_throws_fails_the_invocation_and_rolls_back_the_tools(
            Mode mode, boolean withGuardrails) {
        var run = run(mode, withGuardrails, false);

        assertThat(run.failure()).isInstanceOf(IllegalStateException.class).hasMessage("callback failed");
        // the tool of the callback ran, so it is rolled back too, like the one before it
        assertThat(run.bank().log).containsExactlyInAnyOrder("credit", "debit", "uncredit", "undebit");
        assertThat(run.compensatedEvents())
                .extracting(ToolCompensatedEvent::reason)
                .containsOnly(CompensationReason.TOOL_EXECUTION_FAILED);
        // the call of the callback ended as a failure: its event is an error, then it is rolled back
        assertThat(run.events())
                .filteredOn(event -> isFor(event, "debit"))
                .satisfiesExactly(
                        executed -> assertThat(executed)
                                .asInstanceOf(type(ToolExecutedEvent.class))
                                .returns(true, ToolExecutedEvent::isError)
                                .extracting(ToolExecutedEvent::resultText)
                                .asString()
                                .contains("callback failed"),
                        compensated -> assertThat(compensated).isInstanceOf(ToolCompensatedEvent.class));
    }

    @ParameterizedTest
    @MethodSource("modes")
    void a_before_callback_that_throws_fails_the_invocation_and_rolls_back_the_earlier_tool(
            Mode mode, boolean withGuardrails) {
        var run = run(mode, withGuardrails, true);

        assertThat(run.failure()).isInstanceOf(IllegalStateException.class).hasMessage("callback failed");
        // the tool of the callback never ran, so only the earlier one is rolled back
        assertThat(run.bank().log).containsExactly("credit", "uncredit");
        assertThat(run.compensatedEvents())
                .extracting(ToolCompensatedEvent::reason)
                .containsOnly(CompensationReason.TOOL_EXECUTION_FAILED);
    }

    private static boolean isFor(AiServiceEvent event, String toolName) {
        return ((event instanceof ToolExecutedEvent executed) && executed.request().name().equals(toolName))
                || ((event instanceof ToolCompensatedEvent compensated)
                        && compensated.request().name().equals(toolName));
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
}
