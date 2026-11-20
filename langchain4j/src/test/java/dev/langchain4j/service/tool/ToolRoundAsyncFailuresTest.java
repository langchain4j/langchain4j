package dev.langchain4j.service.tool;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.invocation.InvocationContext;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The non-blocking methods of a {@link ToolRound} report every failure through the returned future, including the ones
 * that happen before anything is started.
 */
class ToolRoundAsyncFailuresTest {

    private static ToolRound round() {
        return new ToolService()
                .startRound(ToolServiceContext.builder().build(), InvocationContext.builder().build(), null);
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
    void settle_async_fails_its_future_for_null_calls() {
        assertThat(round().settleAsync(null))
                .failsWithin(Duration.ofSeconds(5))
                .withThrowableThat()
                .havingRootCause()
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void complete_async_fails_its_future_for_null_calls() {
        assertThat(round().completeAsync(AiMessage.from("hi"), null, null, List.of()))
                .failsWithin(Duration.ofSeconds(5))
                .withThrowableThat()
                .havingRootCause()
                .isInstanceOf(NullPointerException.class);
    }
}
