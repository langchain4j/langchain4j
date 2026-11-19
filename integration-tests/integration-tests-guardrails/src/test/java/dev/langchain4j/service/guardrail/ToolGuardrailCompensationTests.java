package dev.langchain4j.service.guardrail;

import static dev.langchain4j.service.guardrail.ToolGuardrailTestSupport.inputFailure;
import static dev.langchain4j.service.guardrail.ToolGuardrailTestSupport.inputFatal;
import static dev.langchain4j.service.guardrail.ToolGuardrailTestSupport.onInputOf;
import static dev.langchain4j.service.guardrail.ToolGuardrailTestSupport.onOutputOf;
import static dev.langchain4j.service.guardrail.ToolGuardrailTestSupport.outputFailure;
import static dev.langchain4j.service.guardrail.ToolGuardrailTestSupport.outputFatal;
import static dev.langchain4j.service.guardrail.ToolGuardrailTestSupport.toolCall;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agent.tool.CompensateFor;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.guardrail.ToolInputGuardrailException;
import dev.langchain4j.guardrail.ToolOutputGuardrailException;
import dev.langchain4j.guardrail.ToolOutputGuardrailResult;
import dev.langchain4j.observability.api.event.CompensationReason;
import dev.langchain4j.observability.api.event.ToolCompensatedEvent;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.guardrail.ToolGuardrailTestSupport.Mode;
import dev.langchain4j.service.guardrail.ToolGuardrailTestSupport.Run;
import dev.langchain4j.service.tool.ToolExecution;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * What a tool guardrail rejection means for compensation (D11). The result the LLM ends up with decides whether a step
 * failed; the tool as it ran decides whether it is compensable, and its compensating action gets its original result.
 * <p>
 * A <em>fatal</em> rejection is covered in the {@code CompletableFuture} and reactive modes only: the sync and
 * {@code TokenStream} modes do not roll back before rethrowing a tool error yet (a separate upstream change), so a fatal
 * rejection does not either. See
 * {@link #a_fatal_rejection_rolls_back_in_the_non_blocking_modes}.
 */
class ToolGuardrailCompensationTests extends BaseGuardrailTests {

    /**
     * The modes that compensate. {@code TokenStream} is left out: its handler has no compensation at all, with or
     * without guardrails (measured with a tool that throws and no guardrail: {@code credit} is not rolled back there,
     * while sync, {@code CompletableFuture} and reactive roll it back). That is existing behavior, not something tool
     * guardrails change.
     */
    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @ParameterizedTest
    @EnumSource(value = Mode.class, mode = EnumSource.Mode.EXCLUDE, names = "TOKEN_STREAM")
    @interface CompensatingModes {}

    private static final String ROLLED_BACK =
            "Tool '%s' was executed successfully but was rolled back due to failure of tool '%s'";

    /** Tools that all have a compensating action, and one that always fails. */
    public static class Bank {

        final List<String> log = new CopyOnWriteArrayList<>();
        // the result each compensating action received, by the name of the tool
        final Map<String, String> compensatedResults = new ConcurrentHashMap<>();

        @Tool
        public String credit(@P(name = "name") String name) {
            log.add("credit");
            return "credited";
        }

        @CompensateFor("credit")
        public void uncredit(ToolExecution execution) {
            log.add("uncredit");
            compensatedResults.put("credit", execution.result());
        }

        @Tool
        public String debit(@P(name = "name") String name) {
            log.add("debit");
            return "debited";
        }

        @CompensateFor("debit")
        public void undebit(ToolExecution execution) {
            log.add("undebit");
            compensatedResults.put("debit", execution.result());
        }

        @Tool
        public String withdraw(@P(name = "name") String name) {
            log.add("withdraw");
            throw new IllegalStateException("insufficient funds");
        }
    }

    private record Scenario(Bank bank, Run run) {

        List<String> compensatedTools() {
            return run.compensatedTools();
        }

        List<CompensationReason> compensationReasons() {
            return run.compensatedEvents.stream()
                    .map(ToolCompensatedEvent::reason)
                    .toList();
        }
    }

    /** A model that calls the tools of each round in turn, then answers. */
    @SafeVarargs
    private static List<AiMessage> modelCalling(List<String>... rounds) {
        var messages = new ArrayList<AiMessage>();
        for (int round = 0; round < rounds.length; round++) {
            int roundNumber = round;
            messages.add(AiMessage.from(IntStream.range(0, rounds[round].size())
                    .mapToObj(i -> toolCall(
                            "call-%d-%d".formatted(roundNumber, i), rounds[roundNumber].get(i), "{\"name\":\"Mario\"}"))
                    .toList()));
        }
        messages.add(AiMessage.from("done"));
        return messages;
    }

    private static List<AiMessage> modelCalling(String... tools) {
        return modelCalling(List.of(tools));
    }

    private static Scenario run(Mode mode, List<AiMessage> responses, Consumer<AiServices<?>> setup) {
        var bank = new Bank();
        var run = ToolGuardrailTestSupport.run(mode, responses, services -> {
            services.tools(bank).compensateOnToolErrors(true);
            setup.accept(services);
        });
        return new Scenario(bank, run);
    }

    // ---------- output guardrail: success becomes an error ----------

    @CompensatingModes
    void an_output_failure_is_a_failed_step_that_rolls_back_the_tool_too_with_its_original_result(Mode mode) {
        var scenario = run(
                mode,
                modelCalling("credit", "debit"),
                services -> services.toolOutputGuardrails(onOutputOf("debit", request -> outputFailure("blocked"))));

        assertThat(scenario.run().answerText()).isEqualTo("done");
        assertThat(scenario.bank().log).containsExactlyInAnyOrder("credit", "debit", "uncredit", "undebit");
        // the compensating action of the rejected tool gets the result the tool ran with, not the guardrail's message
        assertThat(scenario.bank().compensatedResults)
                .containsEntry("debit", "debited")
                .containsEntry("credit", "credited");
        assertThat(scenario.compensatedTools()).containsExactlyInAnyOrder("credit", "debit");
        assertThat(scenario.compensationReasons()).containsOnly(CompensationReason.TOOL_OUTPUT_GUARDRAIL_FAILED);
        // the LLM sees the guardrail's message verbatim for the rejected tool, the rolled-back text for the others
        assertThat(scenario.run().toolResultMessageOfSecondRequest("debit"))
                .returns(true, ToolExecutionResultMessage::isError)
                .returns("blocked", ToolExecutionResultMessage::text);
        assertThat(scenario.run().toolResultMessageOfSecondRequest("credit").text())
                .isEqualTo(ROLLED_BACK.formatted("credit", "debit"));
        assertThat(scenario.run().toolResultMessagesInMemory().get("debit").text())
                .isEqualTo("blocked");
        assertThat(scenario.run().toolResultMessagesInMemory().get("credit").text())
                .isEqualTo(ROLLED_BACK.formatted("credit", "debit"));
    }

    @CompensatingModes
    void a_rewrite_to_an_error_is_a_failed_step_like_a_failure(Mode mode) {
        var scenario = run(
                mode,
                modelCalling("credit", "debit"),
                services -> services.toolOutputGuardrails(onOutputOf(
                        "debit",
                        request -> ToolOutputGuardrailResult.successWith(List.of(TextContent.from("flagged")), true))));

        assertThat(scenario.bank().log).containsExactlyInAnyOrder("credit", "debit", "uncredit", "undebit");
        assertThat(scenario.compensationReasons()).containsOnly(CompensationReason.TOOL_OUTPUT_GUARDRAIL_FAILED);
        assertThat(scenario.run().toolResultMessageOfSecondRequest("debit").text())
                .isEqualTo("flagged");
        assertThat(scenario.run().toolResultMessageOfSecondRequest("credit").text())
                .isEqualTo(ROLLED_BACK.formatted("credit", "debit"));
    }

    @CompensatingModes
    void a_rewrite_that_stays_a_success_sanitizes_without_a_rollback(Mode mode) {
        var scenario = run(
                mode,
                modelCalling("credit", "debit"),
                services -> services.toolOutputGuardrails(
                        onOutputOf("debit", request -> ToolOutputGuardrailResult.successWith("sanitized"))));

        assertThat(scenario.bank().log).containsExactlyInAnyOrder("credit", "debit");
        assertThat(scenario.run().compensatedEvents).isEmpty();
        assertThat(scenario.run().toolResultMessageOfSecondRequest("debit").text())
                .isEqualTo("sanitized");
        assertThat(scenario.run().toolResultMessageOfSecondRequest("credit").text())
                .isEqualTo("credited");
    }

    // ---------- output guardrail: an error result ----------

    @CompensatingModes
    void an_error_result_sanitized_with_a_text_rewrite_stays_an_error_and_still_rolls_the_others_back(Mode mode) {
        var scenario = run(
                mode,
                modelCalling("credit", "withdraw"),
                services -> services.toolOutputGuardrails(
                        onOutputOf("withdraw", request -> ToolOutputGuardrailResult.successWith("[removed]"))));

        assertThat(scenario.bank().log).containsExactlyInAnyOrder("credit", "withdraw", "uncredit");
        // the tool failed on its own, so it is not a guardrail failure, and the tool of the error is not compensable
        assertThat(scenario.compensatedTools()).containsExactly("credit");
        assertThat(scenario.compensationReasons()).containsOnly(CompensationReason.TOOL_EXECUTION_FAILED);
        assertThat(scenario.run().toolResultMessageOfSecondRequest("withdraw"))
                .returns(true, ToolExecutionResultMessage::isError)
                .returns("[removed]", ToolExecutionResultMessage::text);
    }

    // ---------- input guardrail ----------

    @CompensatingModes
    void an_input_rejection_is_a_failed_step_and_the_rejected_tool_is_not_compensable(Mode mode) {
        var scenario = run(
                mode,
                modelCalling("credit", "debit"),
                services -> services.toolInputGuardrails(onInputOf("debit", request -> inputFailure("denied"))));

        // the rejected tool never ran, so there is nothing to roll back for it
        assertThat(scenario.bank().log).containsExactly("credit", "uncredit");
        assertThat(scenario.compensatedTools()).containsExactly("credit");
        assertThat(scenario.compensationReasons()).containsOnly(CompensationReason.TOOL_INPUT_GUARDRAIL_FAILED);
        assertThat(scenario.run().toolResultMessageOfSecondRequest("debit").text())
                .isEqualTo("denied");
        assertThat(scenario.run().toolResultMessageOfSecondRequest("credit").text())
                .isEqualTo(ROLLED_BACK.formatted("credit", "debit"));
    }

    @CompensatingModes
    void an_input_rejection_in_a_later_round_rolls_back_the_tools_of_the_earlier_rounds(Mode mode) {
        var scenario = run(
                mode,
                modelCalling(List.of("credit"), List.of("debit")),
                services -> services.toolInputGuardrails(onInputOf("debit", request -> inputFailure("denied"))));

        assertThat(scenario.run().answerText()).isEqualTo("done");
        assertThat(scenario.bank().log).containsExactly("credit", "uncredit");
        assertThat(scenario.compensationReasons()).containsOnly(CompensationReason.TOOL_INPUT_GUARDRAIL_FAILED);
        assertThat(scenario.run().toolResultMessagesInMemory().get("credit").text())
                .isEqualTo(ROLLED_BACK.formatted("credit", "debit"));
        assertThat(scenario.run().toolResultMessagesInMemory().get("debit").text())
                .isEqualTo("denied");
    }

    @CompensatingModes
    void an_output_rejection_in_a_later_round_rolls_back_the_tools_of_the_earlier_rounds(Mode mode) {
        var scenario = run(
                mode,
                modelCalling(List.of("credit"), List.of("debit")),
                services -> services.toolOutputGuardrails(onOutputOf("debit", request -> outputFailure("blocked"))));

        assertThat(scenario.run().answerText()).isEqualTo("done");
        assertThat(scenario.bank().log).containsExactlyInAnyOrder("credit", "debit", "uncredit", "undebit");
        assertThat(scenario.compensationReasons()).containsOnly(CompensationReason.TOOL_OUTPUT_GUARDRAIL_FAILED);
        assertThat(scenario.run().toolResultMessagesInMemory().get("credit").text())
                .isEqualTo(ROLLED_BACK.formatted("credit", "debit"));
        assertThat(scenario.run().toolResultMessagesInMemory().get("debit").text())
                .isEqualTo("blocked");
    }

    @CompensatingModes
    void nothing_is_rolled_back_when_compensation_is_off(Mode mode) {
        var scenario = run(
                mode,
                modelCalling("credit", "debit"),
                services -> services.compensateOnToolErrors(false)
                        .toolOutputGuardrails(onOutputOf("debit", request -> outputFailure("blocked"))));

        assertThat(scenario.bank().log).containsExactlyInAnyOrder("credit", "debit");
        assertThat(scenario.run().compensatedEvents).isEmpty();
        assertThat(scenario.run().toolResultMessageOfSecondRequest("credit").text())
                .isEqualTo("credited");
        assertThat(scenario.run().toolResultMessageOfSecondRequest("debit").text())
                .isEqualTo("blocked");
    }

    // ---------- fatal rejections (D11: roll back in all modes once the upstream fix is in) ----------

    @ParameterizedTest
    @EnumSource(value = Mode.class, names = {"COMPLETABLE_FUTURE", "REACTIVE"})
    void a_fatal_rejection_rolls_back_in_the_non_blocking_modes(Mode mode) {
        var output = run(
                mode,
                modelCalling(List.of("credit"), List.of("debit")),
                services -> services.toolOutputGuardrails(onOutputOf("debit", request -> outputFatal("poisoned"))));

        assertThat(output.run().failure()).isInstanceOf(ToolOutputGuardrailException.class);
        assertThat(output.bank().log).containsExactlyInAnyOrder("credit", "debit", "uncredit", "undebit");
        // the tool whose output was rejected ran, so it is rolled back with the result it ran with
        assertThat(output.bank().compensatedResults).containsEntry("debit", "debited");
        assertThat(output.compensationReasons()).containsOnly(CompensationReason.TOOL_OUTPUT_GUARDRAIL_FAILED);

        var input = run(
                mode,
                modelCalling(List.of("credit"), List.of("debit")),
                services -> services.toolInputGuardrails(onInputOf("debit", request -> inputFatal("attack"))));

        assertThat(input.run().failure()).isInstanceOf(ToolInputGuardrailException.class);
        // the rejected tool never ran, so only the earlier one is rolled back
        assertThat(input.bank().log).containsExactly("credit", "uncredit");
        assertThat(input.compensationReasons()).containsOnly(CompensationReason.TOOL_INPUT_GUARDRAIL_FAILED);
    }
}
