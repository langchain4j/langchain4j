package dev.langchain4j.service.tool;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agent.tool.CompensateFor;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.guardrail.ToolInputGuardrail;
import dev.langchain4j.guardrail.ToolInputGuardrailRequest;
import dev.langchain4j.guardrail.ToolInputGuardrailResult;
import dev.langchain4j.observability.api.event.CompensationReason;
import dev.langchain4j.observability.api.event.ToolCompensatedEvent;
import dev.langchain4j.observability.api.listener.ToolCompensatedEventListener;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.test.mock.MockChatModel;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * A callback of the tool execution ({@code beforeToolExecution} / {@code afterToolExecution}) that throws fails the
 * invocation and rolls back the compensable tools, like a tool that throws, with or without tool guardrails.
 */
class ToolCallbackFailureTest {

    enum Mode {
        SYNC,
        COMPLETABLE_FUTURE
    }

    interface SyncAssistant {

        String chat(@UserMessage String message);
    }

    interface FutureAssistant {

        CompletableFuture<String> chat(@UserMessage String message);
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

    private static ToolExecutionRequest call(String id, String tool) {
        return ToolExecutionRequest.builder()
                .id(id)
                .name(tool)
                .arguments("{\"arg0\": \"Mario\"}")
                .build();
    }

    private record Run(Bank bank, List<ToolCompensatedEvent> events, Throwable failure, String answer) {}

    private static Run run(Mode mode, boolean withGuardrails, boolean failBefore) {
        var bank = new Bank();
        var events = new CopyOnWriteArrayList<ToolCompensatedEvent>();
        var model = MockChatModel.thatResponds(
                AiMessage.from(List.of(call("call-0", "credit"), call("call-1", "debit"))), AiMessage.from("done"));
        AiServices<?> builder = (mode == Mode.SYNC)
                ? AiServices.builder(SyncAssistant.class)
                : AiServices.builder(FutureAssistant.class);
        builder.chatModel(model)
                .tools(bank)
                .compensateOnToolErrors(true)
                .registerListener((ToolCompensatedEventListener) events::add);
        if (withGuardrails) {
            builder.toolInputGuardrails(PASSING);
        }
        if (failBefore) {
            builder.beforeToolExecution(before -> failFor(before.request()));
        } else {
            builder.afterToolExecution(after -> failFor(after.request()));
        }

        Object assistant = builder.build();
        try {
            String answer = (mode == Mode.SYNC)
                    ? ((SyncAssistant) assistant).chat("hello")
                    : ((FutureAssistant) assistant).chat("hello").get(10, TimeUnit.SECONDS);
            return new Run(bank, events, null, answer);
        } catch (ExecutionException e) {
            return new Run(bank, events, e.getCause(), null);
        } catch (IllegalStateException e) {
            return new Run(bank, events, e, null);
        } catch (TimeoutException | InterruptedException e) {
            throw new AssertionError(e);
        }
    }

    private static void failFor(ToolExecutionRequest request) {
        if (request.name().equals("debit")) {
            throw new IllegalStateException("callback failed");
        }
    }

    @ParameterizedTest
    @CsvSource({"SYNC, false", "SYNC, true", "COMPLETABLE_FUTURE, false", "COMPLETABLE_FUTURE, true"})
    void an_after_callback_that_throws_fails_the_invocation_and_rolls_back_the_tools(
            Mode mode, boolean withGuardrails) {
        var run = run(mode, withGuardrails, false);

        assertThat(run.failure()).isInstanceOf(IllegalStateException.class).hasMessage("callback failed");
        assertThat(run.answer()).isNull();
        // the tool of the callback ran, so it is rolled back too, like the one before it
        assertThat(run.bank().log).containsExactlyInAnyOrder("credit", "debit", "uncredit", "undebit");
        assertThat(run.events())
                .extracting(ToolCompensatedEvent::reason)
                .containsOnly(CompensationReason.TOOL_EXECUTION_FAILED);
    }

    @ParameterizedTest
    @CsvSource({"SYNC, false", "SYNC, true", "COMPLETABLE_FUTURE, false", "COMPLETABLE_FUTURE, true"})
    void a_before_callback_that_throws_fails_the_invocation_and_rolls_back_the_earlier_tool(
            Mode mode, boolean withGuardrails) {
        var run = run(mode, withGuardrails, true);

        assertThat(run.failure()).isInstanceOf(IllegalStateException.class).hasMessage("callback failed");
        // the tool of the callback never ran, so only the earlier one is rolled back
        assertThat(run.bank().log).containsExactly("credit", "uncredit");
        assertThat(run.events())
                .extracting(ToolCompensatedEvent::reason)
                .containsOnly(CompensationReason.TOOL_EXECUTION_FAILED);
    }
}
