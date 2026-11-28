package dev.langchain4j.service.tool;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agent.tool.CompensateFor;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.guardrail.ToolInputGuardrail;
import dev.langchain4j.guardrail.ToolInputGuardrailException;
import dev.langchain4j.guardrail.ToolInputGuardrailRequest;
import dev.langchain4j.guardrail.ToolInputGuardrailResult;
import dev.langchain4j.guardrail.ToolOutputGuardrail;
import dev.langchain4j.guardrail.ToolOutputGuardrailException;
import dev.langchain4j.guardrail.ToolOutputGuardrailRequest;
import dev.langchain4j.guardrail.ToolOutputGuardrailResult;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.observability.api.event.CompensationReason;
import dev.langchain4j.observability.api.event.ToolCompensatedEvent;
import dev.langchain4j.observability.api.listener.ToolCompensatedEventListener;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.test.mock.MockChatModel;
import java.time.Duration;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;
import java.util.stream.IntStream;

/**
 * What a tool guardrail rejection means for compensation. The result the LLM ends up with decides whether a step
 * failed; the tool as it ran decides whether it is compensable, and its compensating action gets its original result.
 * A fatal rejection fails the invocation like a tool error that is rethrown, so the compensable tools are rolled back
 * before it. The {@code TokenStream} and reactive modes are covered end to end in {@code integration-tests-guardrails}.
 */
class ToolGuardrailCompensationTest {

    private static final String ROLLED_BACK =
            "Tool '%s' was executed successfully but was rolled back because tool '%s' failed";
    // the result factories are default methods of the interfaces, which lambdas cannot call
    private static final ToolInputGuardrail INPUT = request -> null;
    private static final ToolOutputGuardrail OUTPUT = request -> null;

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

    /** Tools that all have a compensating action, and one that always fails. */
    public static class Bank {

        final List<String> log = new CopyOnWriteArrayList<>();
        // the result each compensating action received, by the name of the tool
        final Map<String, String> compensatedResults = new ConcurrentHashMap<>();
        // whether the compensating action of credit fails
        volatile boolean failingRollback;

        @Tool
        String credit(String name) {
            log.add("credit");
            return "credited";
        }

        @CompensateFor("credit")
        void uncredit(ToolExecution execution) {
            log.add("uncredit");
            compensatedResults.put("credit", execution.result());
            if (failingRollback) {
                throw new IllegalStateException("rollback failed");
            }
        }

        @Tool
        String debit(String name) {
            log.add("debit");
            return "debited";
        }

        @CompensateFor("debit")
        void undebit(ToolExecution execution) {
            log.add("undebit");
            compensatedResults.put("debit", execution.result());
        }

        @Tool
        String withdraw(String name) {
            log.add("withdraw");
            throw new IllegalStateException("insufficient funds");
        }
    }

    /** Everything a run of the AI service reports. */
    private static final class Run {

        final Bank bank;
        final MockChatModel model;
        final ChatMemory memory = MessageWindowChatMemory.withMaxMessages(20);
        final List<ToolCompensatedEvent> compensatedEvents = new CopyOnWriteArrayList<>();
        String answer;
        Throwable failure;

        Run(Bank bank, MockChatModel model) {
            this.bank = bank;
            this.model = model;
        }

        /** The tool result messages the LLM got in its request number {@code index}, by tool name. */
        Map<String, ToolExecutionResultMessage> toolMessagesOfRequest(int index) {
            return model.requests().get(index).messages().stream()
                    .filter(ToolExecutionResultMessage.class::isInstance)
                    .map(ToolExecutionResultMessage.class::cast)
                    .collect(Collectors.toMap(ToolExecutionResultMessage::toolName, message -> message));
        }

        /** The tool result messages chat memory holds, by tool name. */
        Map<String, ToolExecutionResultMessage> toolMessagesInMemory() {
            return memory.messages().stream()
                    .filter(ToolExecutionResultMessage.class::isInstance)
                    .map(ToolExecutionResultMessage.class::cast)
                    .collect(Collectors.toMap(ToolExecutionResultMessage::toolName, message -> message));
        }

        List<String> compensatedTools() {
            return compensatedEvents.stream()
                    .map(event -> event.request().name())
                    .toList();
        }

        List<CompensationReason> compensationReasons() {
            return compensatedEvents.stream().map(ToolCompensatedEvent::reason).toList();
        }
    }

    private static ToolExecutionRequest call(String id, String tool) {
        return ToolExecutionRequest.builder()
                .id(id)
                .name(tool)
                .arguments("{\"arg0\": \"Mario\"}")
                .build();
    }

    private static MockChatModel modelCalling(String... tools) {
        return MockChatModel.thatResponds(
                AiMessage.from(IntStream.range(0, tools.length)
                        .mapToObj(i -> call("call-" + i, tools[i]))
                        .toList()),
                AiMessage.from("done"));
    }

    /** A model that calls the tools of each round in turn, then answers. */
    private static MockChatModel modelCallingInRounds(List<String>... rounds) {
        var messages = new ArrayList<AiMessage>();
        for (int round = 0; round < rounds.length; round++) {
            int roundNumber = round;
            messages.add(AiMessage.from(IntStream.range(0, rounds[round].size())
                    .mapToObj(i -> call("call-" + roundNumber + "-" + i, rounds[roundNumber].get(i)))
                    .toList()));
        }
        messages.add(AiMessage.from("done"));
        return MockChatModel.thatResponds(messages);
    }

    private static ToolOutputGuardrail onOutputOf(
            String tool, Function<ToolOutputGuardrailRequest, ToolOutputGuardrailResult> action) {
        return new ToolOutputGuardrail() {

            @Override
            public ToolOutputGuardrailResult validate(ToolOutputGuardrailRequest request) {
                return tool.equals(request.toolName()) ? action.apply(request) : success();
            }

            @Override
            public CompletableFuture<ToolOutputGuardrailResult> validateAsync(ToolOutputGuardrailRequest request) {
                return CompletableFuture.completedFuture(validate(request));
            }
        };
    }

    private static ToolInputGuardrail onInputOf(
            String tool, Function<ToolInputGuardrailRequest, ToolInputGuardrailResult> action) {
        return new ToolInputGuardrail() {

            @Override
            public ToolInputGuardrailResult validate(ToolInputGuardrailRequest request) {
                return tool.equals(request.toolName()) ? action.apply(request) : success();
            }

            @Override
            public CompletableFuture<ToolInputGuardrailResult> validateAsync(ToolInputGuardrailRequest request) {
                return CompletableFuture.completedFuture(validate(request));
            }
        };
    }

    private static Run run(Mode mode, MockChatModel model, Consumer<AiServices<?>> configuration) {
        return run(mode, new Bank(), model, configuration);
    }

    private static Run run(Mode mode, Bank bank, MockChatModel model, Consumer<AiServices<?>> configuration) {
        var run = new Run(bank, model);
        AiServices<?> services = (mode == Mode.SYNC)
                ? AiServices.builder(SyncAssistant.class)
                : AiServices.builder(FutureAssistant.class);
        services.chatModel(model)
                .chatMemory(run.memory)
                .tools(run.bank)
                .compensateOnToolErrors(true)
                .toolExecutionErrorHandler((error, context) -> ToolErrorHandlerResult.text(error.getMessage()))
                .registerListener((ToolCompensatedEventListener) run.compensatedEvents::add);
        configuration.accept(services);

        Object assistant = services.build();
        try {
            if (mode == Mode.SYNC) {
                run.answer = ((SyncAssistant) assistant).chat("hello");
            } else {
                run.answer = ((FutureAssistant) assistant).chat("hello").get(10, TimeUnit.SECONDS);
            }
        } catch (ExecutionException e) {
            run.failure = e.getCause();
        } catch (RuntimeException | TimeoutException e) {
            run.failure = e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            run.failure = e;
        }
        return run;
    }

    // ---------- output guardrail: success becomes an error ----------

    @ParameterizedTest
    @EnumSource(Mode.class)
    void an_output_failure_is_a_failed_step_that_rolls_back_the_tool_too_with_its_original_result(Mode mode) {
        var run = run(
                mode,
                modelCalling("credit", "debit"),
                services -> services.toolOutputGuardrails(onOutputOf("debit", request -> OUTPUT.failure("blocked"))));

        assertThat(run.answer).isEqualTo("done");
        assertThat(run.bank.log).containsExactlyInAnyOrder("credit", "debit", "uncredit", "undebit");
        // the compensating action of the rejected tool gets the result the tool ran with
        assertThat(run.bank.compensatedResults)
                .containsEntry("debit", "debited")
                .containsEntry("credit", "credited");
        assertThat(run.compensatedTools()).containsExactlyInAnyOrder("credit", "debit");
        assertThat(run.compensationReasons()).containsOnly(CompensationReason.TOOL_OUTPUT_GUARDRAIL_FAILED);
        // the LLM sees the message of the guardrail verbatim for the rejected tool, the rolled-back text for the others
        assertThat(run.toolMessagesOfRequest(1)).hasSize(2).satisfies(messages -> {
            assertThat(messages.get("debit").text()).isEqualTo("blocked");
            assertThat(messages.get("debit").isError()).isTrue();
            assertThat(messages.get("credit").text()).isEqualTo(ROLLED_BACK.formatted("credit", "debit"));
        });
        assertThat(run.toolMessagesInMemory().get("debit").text()).isEqualTo("blocked");
        assertThat(run.toolMessagesInMemory().get("credit").text()).isEqualTo(ROLLED_BACK.formatted("credit", "debit"));
    }

    @ParameterizedTest
    @EnumSource(Mode.class)
    void a_rewrite_to_an_error_is_a_failed_step_like_a_failure(Mode mode) {
        var run = run(
                mode,
                modelCalling("credit", "debit"),
                services -> services.toolOutputGuardrails(onOutputOf(
                        "debit", request -> OUTPUT.successWith(List.of(TextContent.from("flagged")), true))));

        assertThat(run.bank.log).containsExactlyInAnyOrder("credit", "debit", "uncredit", "undebit");
        assertThat(run.compensationReasons()).containsOnly(CompensationReason.TOOL_OUTPUT_GUARDRAIL_FAILED);
        assertThat(run.toolMessagesOfRequest(1).get("debit").text()).isEqualTo("flagged");
        assertThat(run.toolMessagesOfRequest(1).get("credit").text())
                .isEqualTo(ROLLED_BACK.formatted("credit", "debit"));
    }

    @ParameterizedTest
    @EnumSource(Mode.class)
    void a_rewrite_that_stays_a_success_sanitizes_without_a_rollback(Mode mode) {
        var run = run(
                mode,
                modelCalling("credit", "debit"),
                services ->
                        services.toolOutputGuardrails(onOutputOf("debit", request -> OUTPUT.successWith("sanitized"))));

        assertThat(run.bank.log).containsExactlyInAnyOrder("credit", "debit");
        assertThat(run.compensatedEvents).isEmpty();
        assertThat(run.toolMessagesOfRequest(1).get("debit").text()).isEqualTo("sanitized");
        assertThat(run.toolMessagesOfRequest(1).get("credit").text()).isEqualTo("credited");
    }

    @ParameterizedTest
    @EnumSource(Mode.class)
    void a_sanitized_tool_that_is_rolled_back_reports_the_sanitized_result_and_its_action_gets_the_raw_one(Mode mode) {
        var run = run(
                mode,
                modelCalling("credit", "withdraw"),
                services -> services.toolOutputGuardrails(
                        onOutputOf("credit", request -> OUTPUT.successWith("credited <redacted>"))));

        assertThat(run.bank.log).containsExactlyInAnyOrder("credit", "withdraw", "uncredit");
        // the compensating action gets the result the tool ran with: the undo may need what the guardrail redacted
        assertThat(run.bank.compensatedResults).containsExactly(Map.entry("credit", "credited"));
        // the event carries what the LLM saw before the rollback, like the ToolExecutedEvent
        assertThat(run.compensatedEvents)
                .singleElement()
                .returns("credit", event -> event.request().name())
                .returns("credited <redacted>", ToolCompensatedEvent::resultText);
    }

    @ParameterizedTest
    @EnumSource(Mode.class)
    void a_tool_whose_output_guardrail_failed_reports_the_error_result_and_its_action_gets_the_raw_one(Mode mode) {
        var run = run(
                mode,
                modelCalling("credit", "debit"),
                services -> services.toolOutputGuardrails(onOutputOf("debit", request -> OUTPUT.failure("blocked"))));

        assertThat(run.bank.compensatedResults).containsEntry("debit", "debited");
        assertThat(run.compensatedEvents)
                .filteredOn(event -> event.request().name().equals("debit"))
                .singleElement()
                .returns("blocked", ToolCompensatedEvent::resultText);
    }

    @ParameterizedTest
    @EnumSource(Mode.class)
    void the_final_result_decides_so_a_chain_that_flips_back_to_a_success_is_not_a_failed_step(Mode mode) {
        var run = run(
                mode,
                modelCalling("credit", "debit"),
                services -> services.toolOutputGuardrails(
                        onOutputOf("debit", request -> OUTPUT.successWith(List.of(TextContent.from("flagged")), true)),
                        onOutputOf("debit", request -> OUTPUT.successWith(List.of(TextContent.from("clean")), false))));

        assertThat(run.bank.log).containsExactlyInAnyOrder("credit", "debit");
        assertThat(run.compensatedEvents).isEmpty();
        assertThat(run.toolMessagesOfRequest(1).get("debit").text()).isEqualTo("clean");
    }

    // ---------- output guardrail: an error result ----------

    @ParameterizedTest
    @EnumSource(Mode.class)
    void an_error_result_sanitized_with_a_text_rewrite_stays_a_failed_step(Mode mode) {
        var run = run(
                mode,
                modelCalling("credit", "withdraw"),
                services -> services.toolOutputGuardrails(
                        onOutputOf("withdraw", request -> OUTPUT.successWith("[removed]"))));

        assertThat(run.bank.log).containsExactlyInAnyOrder("credit", "withdraw", "uncredit");
        // the tool failed on its own, so it is not a guardrail failure, and the tool of the error is not compensable
        assertThat(run.compensatedTools()).containsExactly("credit");
        assertThat(run.compensationReasons()).containsOnly(CompensationReason.TOOL_EXECUTION_FAILED);
        assertThat(run.toolMessagesOfRequest(1).get("withdraw").text()).isEqualTo("[removed]");
        assertThat(run.toolMessagesOfRequest(1).get("withdraw").isError()).isTrue();
    }

    @ParameterizedTest
    @EnumSource(Mode.class)
    void an_error_result_that_a_guardrail_flips_to_a_success_is_not_a_failed_step(Mode mode) {
        var run = run(
                mode,
                modelCalling("credit", "withdraw"),
                services -> services.toolOutputGuardrails(onOutputOf(
                        "withdraw", request -> OUTPUT.successWith(List.of(TextContent.from("recovered")), false))));

        assertThat(run.bank.log).containsExactlyInAnyOrder("credit", "withdraw");
        assertThat(run.compensatedEvents).isEmpty();
        assertThat(run.toolMessagesOfRequest(1).get("withdraw").text()).isEqualTo("recovered");
        assertThat(run.toolMessagesOfRequest(1).get("withdraw").isError()).isFalse();
    }

    // ---------- input guardrail ----------

    @ParameterizedTest
    @EnumSource(Mode.class)
    void an_input_rejection_is_a_failed_step_and_the_rejected_tool_is_not_compensable(Mode mode) {
        var run = run(
                mode,
                modelCalling("credit", "debit"),
                services -> services.toolInputGuardrails(onInputOf("debit", request -> INPUT.failure("denied"))));

        // the rejected tool never ran, so there is nothing to roll back for it
        assertThat(run.bank.log).containsExactly("credit", "uncredit");
        assertThat(run.compensatedTools()).containsExactly("credit");
        assertThat(run.compensationReasons()).containsOnly(CompensationReason.TOOL_INPUT_GUARDRAIL_FAILED);
        assertThat(run.toolMessagesOfRequest(1).get("debit").text()).isEqualTo("denied");
        assertThat(run.toolMessagesOfRequest(1).get("credit").text())
                .isEqualTo(ROLLED_BACK.formatted("credit", "debit"));
    }

    // ---------- failures that are not the guardrails' ----------

    @ParameterizedTest
    @EnumSource(Mode.class)
    void a_callback_that_throws_before_the_guardrails_run_is_not_a_guardrail_failure(Mode mode) {
        var run = run(
                mode,
                modelCalling("credit", "debit"),
                services -> services.toolInputGuardrails(onInputOf("debit", request -> INPUT.success()))
                        .beforeToolExecution(before -> {
                            if (before.request().name().equals("debit")) {
                                throw new IllegalStateException("callback failed");
                            }
                        }));

        assertThat(run.failure).isInstanceOf(IllegalStateException.class).hasMessage("callback failed");
        // handled as any other failure (probed: the same without guardrails): the earlier tool is rolled back, with the
        // generic reason
        assertThat(run.compensatedTools()).containsExactly("credit");
        assertThat(run.compensationReasons()).containsOnly(CompensationReason.TOOL_EXECUTION_FAILED);
    }

    // ---------- mixed rounds and rounds in sequence ----------

    @ParameterizedTest
    @EnumSource(Mode.class)
    void the_first_failed_call_of_a_mixed_round_decides_the_reason_when_a_tool_error_comes_first(Mode mode) {
        var run = run(
                mode,
                modelCalling("credit", "withdraw", "debit"),
                services -> services.toolOutputGuardrails(onOutputOf("debit", request -> OUTPUT.failure("blocked"))));

        assertThat(run.bank.log).containsExactlyInAnyOrder("credit", "withdraw", "debit", "uncredit", "undebit");
        assertThat(run.compensatedTools()).containsExactlyInAnyOrder("credit", "debit");
        assertThat(run.compensationReasons()).containsOnly(CompensationReason.TOOL_EXECUTION_FAILED);
        assertThat(run.toolMessagesOfRequest(1).get("debit").text()).isEqualTo("blocked");
        assertThat(run.toolMessagesOfRequest(1).get("credit").text())
                .isEqualTo(ROLLED_BACK.formatted("credit", "withdraw"));
    }

    @ParameterizedTest
    @EnumSource(Mode.class)
    void the_first_failed_call_of_a_mixed_round_decides_the_reason_when_a_guardrail_rejection_comes_first(Mode mode) {
        var run = run(
                mode,
                modelCalling("credit", "debit", "withdraw"),
                services -> services.toolOutputGuardrails(onOutputOf("debit", request -> OUTPUT.failure("blocked"))));

        assertThat(run.compensatedTools()).containsExactlyInAnyOrder("credit", "debit");
        assertThat(run.compensationReasons()).containsOnly(CompensationReason.TOOL_OUTPUT_GUARDRAIL_FAILED);
        assertThat(run.toolMessagesOfRequest(1).get("credit").text())
                .isEqualTo(ROLLED_BACK.formatted("credit", "debit"));
    }

    @SuppressWarnings("unchecked")
    @ParameterizedTest
    @EnumSource(Mode.class)
    void a_rejection_in_a_later_round_rolls_back_the_tools_of_the_earlier_rounds(Mode mode) {
        var run = run(
                mode,
                modelCallingInRounds(List.of("credit"), List.of("debit")),
                services -> services.toolOutputGuardrails(onOutputOf("debit", request -> OUTPUT.failure("blocked"))));

        assertThat(run.answer).isEqualTo("done");
        assertThat(run.bank.log).containsExactlyInAnyOrder("credit", "debit", "uncredit", "undebit");
        assertThat(run.compensationReasons()).containsOnly(CompensationReason.TOOL_OUTPUT_GUARDRAIL_FAILED);
        assertThat(run.toolMessagesInMemory().get("credit").text()).isEqualTo(ROLLED_BACK.formatted("credit", "debit"));
        assertThat(run.toolMessagesInMemory().get("debit").text()).isEqualTo("blocked");
    }

    @ParameterizedTest
    @EnumSource(Mode.class)
    void nothing_is_rolled_back_when_compensation_is_off(Mode mode) {
        var run = run(
                mode,
                modelCalling("credit", "debit"),
                services -> services.compensateOnToolErrors(false)
                        .toolOutputGuardrails(onOutputOf("debit", request -> OUTPUT.failure("blocked"))));

        assertThat(run.bank.log).containsExactlyInAnyOrder("credit", "debit");
        assertThat(run.compensatedEvents).isEmpty();
        assertThat(run.toolMessagesOfRequest(1).get("credit").text()).isEqualTo("credited");
        assertThat(run.toolMessagesOfRequest(1).get("debit").text()).isEqualTo("blocked");
    }

    // ---------- fatal rejections ----------

    @SuppressWarnings("unchecked")
    @ParameterizedTest
    @EnumSource(Mode.class)
    void a_fatal_output_failure_rolls_back_the_tools_of_earlier_rounds_and_the_tool_that_ran(Mode mode) {
        var run = run(
                mode,
                modelCallingInRounds(List.of("credit"), List.of("debit")),
                services -> services.toolOutputGuardrails(onOutputOf("debit", request -> OUTPUT.fatal("poisoned"))));

        assertThat(run.failure).isInstanceOf(ToolOutputGuardrailException.class);
        assertThat(run.bank.log).containsExactlyInAnyOrder("credit", "debit", "uncredit", "undebit");
        // the tool whose output was rejected ran, so it is rolled back with the result it ran with
        assertThat(run.bank.compensatedResults)
                .containsEntry("debit", "debited")
                .containsEntry("credit", "credited");
        assertThat(run.compensatedTools()).containsExactlyInAnyOrder("credit", "debit");
        assertThat(run.compensationReasons()).containsOnly(CompensationReason.TOOL_OUTPUT_GUARDRAIL_FAILED);
        // the result of the earlier round is in the chat memory, so it is rewritten there
        assertThat(run.toolMessagesInMemory().get("credit").text()).isEqualTo(ROLLED_BACK.formatted("credit", "debit"));
    }

    @SuppressWarnings("unchecked")
    @ParameterizedTest
    @EnumSource(Mode.class)
    void a_fatal_input_failure_rolls_back_the_tools_of_earlier_rounds_but_not_the_rejected_tool(Mode mode) {
        var run = run(
                mode,
                modelCallingInRounds(List.of("credit"), List.of("debit")),
                services -> services.toolInputGuardrails(onInputOf("debit", request -> INPUT.fatal("attack"))));

        assertThat(run.failure).isInstanceOf(ToolInputGuardrailException.class);
        assertThat(run.bank.log).containsExactly("credit", "uncredit");
        assertThat(run.compensatedTools()).containsExactly("credit");
        assertThat(run.compensationReasons()).containsOnly(CompensationReason.TOOL_INPUT_GUARDRAIL_FAILED);
        assertThat(run.toolMessagesInMemory().get("credit").text()).isEqualTo(ROLLED_BACK.formatted("credit", "debit"));
    }

    @ParameterizedTest
    @EnumSource(Mode.class)
    void a_fatal_failure_rolls_back_the_tools_of_the_same_round(Mode mode) {
        var run = run(
                mode,
                modelCalling("credit", "debit"),
                services -> services.toolInputGuardrails(onInputOf("debit", request -> INPUT.fatal("attack"))));

        assertThat(run.failure).isInstanceOf(ToolInputGuardrailException.class);
        assertThat(run.bank.log).containsExactly("credit", "uncredit");
        assertThat(run.compensatedTools()).containsExactly("credit");
        assertThat(run.compensationReasons()).containsOnly(CompensationReason.TOOL_INPUT_GUARDRAIL_FAILED);
    }

    @ParameterizedTest
    @EnumSource(Mode.class)
    void a_compensating_action_that_fails_is_attached_to_the_fatal_failure(Mode mode) {
        var bank = new Bank();
        bank.failingRollback = true;

        var run = run(
                mode,
                bank,
                modelCalling("credit", "debit"),
                services -> services.toolInputGuardrails(onInputOf("debit", request -> INPUT.fatal("attack"))));

        assertThat(run.failure)
                .isInstanceOf(ToolInputGuardrailException.class)
                .satisfies(failure -> assertThat(failure.getSuppressed())
                        .singleElement()
                        .satisfies(suppressed -> assertThat(suppressed).hasMessage("rollback failed")));
        assertThat(run.compensatedEvents)
                .singleElement()
                .satisfies(event -> assertThat(event.compensationError()).hasMessage("rollback failed"));
    }

    // ---------- cancellation ----------

    @Test
    void a_cancellation_cancels_an_output_validation_in_flight_and_rolls_back_the_tool_that_ran()
            throws InterruptedException {
        var bank = new Bank();
        var model = modelCalling("credit");
        var validations = new CopyOnWriteArrayList<CompletableFuture<ToolOutputGuardrailResult>>();
        var validating = new CountDownLatch(1);
        var compensated = new CompletableFuture<ToolCompensatedEvent>();
        ToolOutputGuardrail neverCompleting = new ToolOutputGuardrail() {

            @Override
            public ToolOutputGuardrailResult validate(ToolOutputGuardrailRequest request) {
                throw new UnsupportedOperationException("validated asynchronously only");
            }

            @Override
            public CompletableFuture<ToolOutputGuardrailResult> validateAsync(ToolOutputGuardrailRequest request) {
                var validation = new CompletableFuture<ToolOutputGuardrailResult>();
                validations.add(validation);
                validating.countDown();
                return validation;
            }
        };
        var assistant = AiServices.builder(FutureAssistant.class)
                .chatModel(model)
                .tools(bank)
                .compensateOnToolErrors(true)
                .toolOutputGuardrails(neverCompleting)
                .registerListener((ToolCompensatedEventListener) compensated::complete)
                .build();

        var answer = assistant.chat("hello");
        assertThat(validating.await(10, TimeUnit.SECONDS)).isTrue();
        answer.cancel(true);

        assertThat(validations)
                .singleElement()
                .satisfies(validation -> assertThat(validation)
                        .failsWithin(Duration.ofSeconds(10))
                        .withThrowableThat()
                        .isInstanceOf(CancellationException.class));
        assertThat(compensated)
                .succeedsWithin(Duration.ofSeconds(10))
                .returns("credit", event -> event.request().name())
                .returns(CompensationReason.INVOCATION_CANCELLED, ToolCompensatedEvent::reason);
        assertThat(bank.log).containsExactly("credit", "uncredit");
        assertThat(bank.compensatedResults).containsEntry("credit", "credited");
        assertThat(model.requests()).hasSize(1);
    }
}
