package dev.langchain4j.service.tool;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.guardrail.ToolInputGuardrail;
import dev.langchain4j.guardrail.ToolInputGuardrailRequest;
import dev.langchain4j.guardrail.ToolInputGuardrailResult;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.service.guardrail.ToolGuardrailService;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

/**
 * Canceling a non-blocking call whose input guardrail completed on another thread, so that its tool is started on the
 * executor of the tools: the cancellation reaches the tool, whether it is running or still waiting in the queue of the
 * executor.
 */
class ToolRoundExecutorHopCancellationTest {

    interface Assistant {}

    private static final ToolSpecification SEARCH =
            ToolSpecification.builder().name("search").build();
    private static final ToolExecutionRequest CALL = ToolExecutionRequest.builder()
            .id("1")
            .name("search")
            .arguments("{}")
            .build();

    /** An executor that queues its tasks until the test runs them. */
    private static final class PausedExecutor implements Executor {

        final List<Runnable> tasks = new ArrayList<>();

        @Override
        public void execute(Runnable task) {
            tasks.add(task);
        }
    }

    /** An input guardrail whose non-blocking validation completes when the test completes {@link #pending}. */
    private static final class PendingInputGuardrail implements ToolInputGuardrail {

        final CompletableFuture<ToolInputGuardrailResult> pending = new CompletableFuture<>();

        @Override
        public ToolInputGuardrailResult validate(ToolInputGuardrailRequest request) {
            return success();
        }

        @Override
        public CompletableFuture<ToolInputGuardrailResult> validateAsync(ToolInputGuardrailRequest request) {
            return pending;
        }
    }

    private final PendingInputGuardrail guardrail = new PendingInputGuardrail();
    private final PausedExecutor executor = new PausedExecutor();
    private final AtomicInteger toolStarts = new AtomicInteger();

    private ToolRound round(CompletableFuture<?> cancellation) {
        var service = ToolGuardrailService.builder(Assistant.class).build();
        var tool = AiServiceTool.builder()
                .toolSpecification(SEARCH)
                .toolExecutor((request, memoryId) -> "unused")
                .toolInputGuardrails(guardrail)
                .build();
        var context = ToolServiceContext.builder()
                .toolGuardrailBindings(Map.of("search", ToolGuardrailBinding.bind(service, null, tool)))
                .build();
        return new ToolService().startRound(context, InvocationContext.builder().build(), null, cancellation);
    }

    private Function<ToolExecutionRequest, CompletableFuture<ToolExecutionResult>> tool(
            CompletableFuture<ToolExecutionResult> toolFuture) {
        return request -> {
            toolStarts.incrementAndGet();
            return toolFuture;
        };
    }

    /**
     * Starts the call, and completes the input guardrail once the call has returned, so the tool is queued on the
     * executor instead of being started on the thread of the guardrail.
     */
    private CompletableFuture<ToolExecutionResult> startCallAndQueueItsTool(
            ToolRound round, CompletableFuture<ToolExecutionResult> toolFuture) {
        var call = round.executeAsync(CALL, tool(toolFuture), CompletableFuture::failedFuture, executor);
        guardrail.pending.complete(ToolInputGuardrailResult.success());

        assertThat(executor.tasks).hasSize(1);
        assertThat(toolStarts).hasValue(0);
        return call;
    }

    @Test
    void canceling_the_call_while_its_tool_runs_cancels_the_future_of_the_tool() {
        var toolFuture = new CompletableFuture<ToolExecutionResult>();
        var call = startCallAndQueueItsTool(round(null), toolFuture);
        executor.tasks.get(0).run();

        call.cancel(true);

        assertThat(toolStarts).hasValue(1);
        assertThat(toolFuture).isCancelled();
        assertThat(call).isCancelled();
    }

    @Test
    void canceling_the_call_while_its_tool_waits_in_the_queue_of_the_executor_keeps_the_tool_from_starting() {
        var call = startCallAndQueueItsTool(round(null), new CompletableFuture<>());

        call.cancel(true);
        executor.tasks.get(0).run();

        assertThat(toolStarts).hasValue(0);
        assertThat(call).isCancelled();
    }

    @Test
    void canceling_the_round_while_the_tool_waits_in_the_queue_of_the_executor_keeps_the_tool_from_starting() {
        var cancellation = new CompletableFuture<Void>();
        var call = startCallAndQueueItsTool(round(cancellation), new CompletableFuture<>());

        cancellation.cancel(true);
        executor.tasks.get(0).run();

        assertThat(toolStarts).hasValue(0);
        assertThat(call)
                .failsWithin(Duration.ofSeconds(5))
                .withThrowableThat()
                .havingRootCause()
                .isInstanceOf(CancellationException.class);
    }

    @Test
    void the_result_of_a_tool_started_on_the_executor_is_the_result_of_the_call() {
        var toolFuture = new CompletableFuture<ToolExecutionResult>();
        var call = startCallAndQueueItsTool(round(null), toolFuture);
        executor.tasks.get(0).run();

        toolFuture.complete(ToolExecutionResult.builder().resultText("found").build());

        assertThat(call)
                .succeedsWithin(Duration.ofSeconds(5))
                .returns("found", ToolExecutionResult::resultText);
    }
}
