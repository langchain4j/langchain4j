package dev.langchain4j.service.tool;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.invocation.InvocationContext;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.Test;

/**
 * The non-blocking methods of a {@link ToolRound} report every failure through the returned future, including the ones
 * that happen before anything is started.
 */
class ToolRoundAsyncFailuresTest {

    private static final InvocationContext INVOCATION = InvocationContext.builder().build();

    private static ToolRound round() {
        return new ToolService().startRound(ToolServiceContext.builder().build(), INVOCATION, null);
    }

    @Test
    void start_tool_fails_its_future_for_a_null_call() {
        assertThat(round().startTool(null, null, null, Runnable::run))
                .failsWithin(Duration.ofSeconds(5))
                .withThrowableThat()
                .havingRootCause()
                .isInstanceOf(IllegalArgumentException.class)
                .withMessageContaining("call");
    }

    @Test
    void complete_async_fails_its_future_for_null_calls() {
        assertThat(round().completeAsync(null))
                .failsWithin(Duration.ofSeconds(5))
                .withThrowableThat()
                .havingRootCause()
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void execute_tool_on_an_executor_fails_its_future_for_a_null_call() {
        assertThat(round().executeTool(null, null, null, Runnable::run))
                .failsWithin(Duration.ofSeconds(5))
                .withThrowableThat()
                .havingRootCause()
                .isInstanceOf(IllegalArgumentException.class)
                .withMessageContaining("call");
    }

    @Test
    void execute_tool_on_an_executor_fails_its_future_and_records_the_call_when_the_executor_throws_an_error() {
        var error = new AssertionError("broken executor");
        Executor throwing = task -> {
            throw error;
        };
        var call = ToolExecutionRequest.builder()
                .id("1")
                .name("tool")
                .arguments("{}")
                .build();
        var round = round();

        assertThat(round.executeTool(call, null, null, throwing))
                .failsWithin(Duration.ofSeconds(5))
                .withThrowableOfType(ExecutionException.class)
                .withCause(error);
        // the round reports it like a call that threw
        assertThat(round.error(call)).containsSame(error);
    }

    @Test
    void compensate_collected_round_async_fails_its_future_for_a_null_round() {
        var toolService = new ToolService();
        toolService.compensateOnToolErrors(true);

        assertThat(toolService.compensateCollectedRoundAsync(
                        null,
                        List.of(),
                        toolService.newCompensableExecutionsAccumulator(),
                        true,
                        null,
                        List.of(),
                        INVOCATION,
                        null,
                        null,
                        Optional.empty(),
                        null))
                .failsWithin(Duration.ofSeconds(5))
                .withThrowableThat()
                .havingRootCause()
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void compensate_collected_round_async_fails_its_future_for_a_null_failed_step() {
        var toolService = new ToolService();
        var round = round();

        assertThat(toolService.compensateCollectedRoundAsync(
                        round.completeAsync(List.of()).join(),
                        List.of(),
                        null,
                        false,
                        null,
                        List.of(),
                        INVOCATION,
                        null,
                        null,
                        null,
                        null))
                .failsWithin(Duration.ofSeconds(5))
                .withThrowableThat()
                .havingRootCause()
                .isInstanceOf(NullPointerException.class);
    }
}
