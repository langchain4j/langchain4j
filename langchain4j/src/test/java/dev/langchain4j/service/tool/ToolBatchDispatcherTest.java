package dev.langchain4j.service.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.invocation.InvocationContext;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Validates {@link ToolBatchDispatcher}: the reusable primitive shared between sync and streaming
 * tool batch dispatch.
 */
class ToolBatchDispatcherTest {

    private ExecutorService executor;

    @BeforeEach
    void setUp() {
        executor = Executors.newFixedThreadPool(8);
    }

    @AfterEach
    void tearDown() {
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    private static ToolExecutionRequest req(String id, String name, String arg) {
        return ToolExecutionRequest.builder()
                .id(id)
                .name(name)
                .arguments("{\"arg0\":\"" + arg + "\"}")
                .build();
    }

    private static Map<String, ToolExecutor> exec(String name, ToolExecutor toolExecutor) {
        Map<String, ToolExecutor> m = new HashMap<>();
        m.put(name, toolExecutor);
        return m;
    }

    private static InvocationContext invocationContext() {
        return InvocationContext.builder()
                .interfaceName("TestService")
                .methodName("chat")
                .userMessage(UserMessage.from("test"))
                .chatMemoryId("default")
                .timestampNow()
                .build();
    }

    private static ToolBatchDispatcher.Request.Builder baseReq() {
        return ToolBatchDispatcher.Request.builder()
                .invocationContext(invocationContext())
                .argumentsErrorHandler((err, ctx) -> {
                    throw err instanceof RuntimeException re ? re : new RuntimeException(err);
                })
                .executionErrorHandler((err, ctx) -> ToolErrorHandlerResult.text(err.getMessage()));
    }

    @Test
    void serial_path_when_executor_is_null_runs_in_calling_thread_and_preserves_order() {
        List<String> orderRun = new ArrayList<>();
        Thread caller = Thread.currentThread();
        AtomicBoolean otherThread = new AtomicBoolean(false);

        ToolExecutor te = (request, memId) -> {
            if (Thread.currentThread() != caller) {
                otherThread.set(true);
            }
            orderRun.add(request.id());
            return "ok-" + request.id();
        };
        Map<String, ToolExecutor> executors = exec("t", te);
        List<ToolExecutionRequest> requests =
                List.of(req("a", "t", "1"), req("b", "t", "2"), req("c", "t", "3"));

        Map<ToolExecutionRequest, ToolExecutionResult> results = ToolBatchDispatcher.dispatch(
                baseReq().toolRequests(requests).toolExecutors(executors).build());

        // Ordered gather: keys preserve original order.
        List<String> resultIdsInOrder = new ArrayList<>();
        results.keySet().forEach(r -> resultIdsInOrder.add(r.id()));
        assertThat(resultIdsInOrder).containsExactly("a", "b", "c");
        assertThat(orderRun).containsExactly("a", "b", "c");
        assertThat(otherThread).isFalse();
    }

    @Test
    void serial_path_when_only_one_request_even_with_executor_runs_in_calling_thread() {
        Thread caller = Thread.currentThread();
        AtomicBoolean otherThread = new AtomicBoolean(false);
        ToolExecutor te = (request, memId) -> {
            if (Thread.currentThread() != caller) {
                otherThread.set(true);
            }
            return "ok";
        };

        Map<ToolExecutionRequest, ToolExecutionResult> results = ToolBatchDispatcher.dispatch(baseReq()
                .toolRequests(List.of(req("a", "t", "1")))
                .toolExecutors(exec("t", te))
                .executor(executor)
                .build());

        assertThat(results).hasSize(1);
        assertThat(otherThread).as("single-request batch should not switch threads").isFalse();
    }

    @Test
    void single_request_uses_executor_when_explicitly_enabled() {
        Thread caller = Thread.currentThread();
        AtomicBoolean otherThread = new AtomicBoolean(false);
        ToolExecutor te = (request, memId) -> {
            if (Thread.currentThread() != caller) {
                otherThread.set(true);
            }
            return "ok";
        };

        Map<ToolExecutionRequest, ToolExecutionResult> results = ToolBatchDispatcher.dispatch(baseReq()
                .toolRequests(List.of(req("a", "t", "1")))
                .toolExecutors(exec("t", te))
                .executor(executor)
                .useExecutorForSingleTool(true)
                .build());

        assertThat(results).hasSize(1);
        assertThat(otherThread).as("explicit opt-in should dispatch a single tool on the executor").isTrue();
    }

    @Test
    void parallel_path_when_executor_and_multiple_requests_runs_concurrently_and_preserves_order()
            throws InterruptedException {
        AtomicInteger inFlight = new AtomicInteger();
        AtomicInteger maxInFlight = new AtomicInteger();
        ToolExecutor te = (request, memId) -> {
            int now = inFlight.incrementAndGet();
            maxInFlight.accumulateAndGet(now, Math::max);
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            inFlight.decrementAndGet();
            return "ok-" + request.id();
        };

        List<ToolExecutionRequest> requests = List.of(
                req("a", "t", "1"), req("b", "t", "2"), req("c", "t", "3"), req("d", "t", "4"));

        Map<ToolExecutionRequest, ToolExecutionResult> results = ToolBatchDispatcher.dispatch(baseReq()
                .toolRequests(requests)
                .toolExecutors(exec("t", te))
                .executor(executor)
                .build());

        // Order preserved.
        List<String> ids = new ArrayList<>();
        results.keySet().forEach(r -> ids.add(r.id()));
        assertThat(ids).containsExactly("a", "b", "c", "d");
        // Concurrency observed (parallel execution).
        assertThat(maxInFlight.get()).isGreaterThan(1);
    }

    @Test
    void cap_check_is_atomic_no_tool_runs_when_cap_exceeded() {
        AtomicInteger calls = new AtomicInteger();
        ToolExecutor te = (request, memId) -> {
            calls.incrementAndGet();
            return "ok";
        };

        List<ToolExecutionRequest> requests =
                List.of(req("a", "t", "1"), req("b", "t", "2"), req("c", "t", "3"));

        assertThatExceptionOfType(ToolCallsLimitExceededException.class)
                .isThrownBy(() -> ToolBatchDispatcher.dispatch(baseReq()
                        .toolRequests(requests)
                        .toolExecutors(exec("t", te))
                        .executor(executor)
                        .maxToolCallsPerResponse(2)
                        .build()))
                .matches(ex -> ex.getLimit() == 2 && ex.getAttempted() == 3);

        assertThat(calls.get()).as("no tool must run when cap is exceeded").isZero();
    }

    @Test
    void on_first_failure_dispatch_propagates_and_does_not_block_on_slow_siblings() throws Exception {
        // Use errorHandlerBypass to force the failer's exception to propagate up out of the
        // dispatcher (instead of being routed through the execution error handler and turned
        // into a tool-result text). This is the path on which sibling cancellation fires.
        //
        // CompletableFuture.cancel(true) does not interrupt running tasks on a supplyAsync
        // executor; this is a documented JDK limitation. The dispatcher's contract is that
        // it (a) returns promptly with the bypassed exception and (b) marks sibling futures
        // cancelled (best-effort). Tasks already running on the executor may continue to run.
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            ToolExecutor failer = (request, memId) -> {
                // Brief delay so the dispatcher sees siblings in flight.
                try {
                    Thread.sleep(20);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                throw new RuntimeException("boom");
            };
            CountDownLatch siblingsRelease = new CountDownLatch(1);
            ToolExecutor slowSibling = (request, memId) -> {
                try {
                    siblingsRelease.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return "done";
            };
            Map<String, ToolExecutor> executors = new HashMap<>();
            executors.put("fail", failer);
            executors.put("slow", slowSibling);

            List<ToolExecutionRequest> requests =
                    List.of(req("a", "fail", "1"), req("b", "slow", "2"), req("c", "slow", "3"));

            long start = System.nanoTime();
            assertThatThrownBy(() -> ToolBatchDispatcher.dispatch(baseReq()
                            .toolRequests(requests)
                            .toolExecutors(executors)
                            .executor(pool)
                            .errorHandlerBypass(
                                    t -> t instanceof RuntimeException && t.getMessage().equals("boom"))
                            .build()))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("boom");
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;

            // The key cancellation-related assertion: dispatch must not wait for slow siblings
            // to finish. Without sibling cancellation, dispatch would block on .get() of every
            // sibling and only return once the 5s release happened.
            assertThat(elapsedMs)
                    .as("dispatch must not wait for slow siblings after first failure (was %d ms)", elapsedMs)
                    .isLessThan(1000);
        } finally {
            pool.shutdownNow();
            pool.awaitTermination(2, TimeUnit.SECONDS);
        }
    }

    @Test
    void error_handler_bypass_propagates_marker_exception_unchanged() {
        class GuardrailViolation extends RuntimeException {
            GuardrailViolation(String msg) {
                super(msg);
            }
        }

        ToolExecutor failer = (request, memId) -> {
            throw new GuardrailViolation("denied");
        };

        AtomicBoolean errorHandlerInvoked = new AtomicBoolean();
        ToolExecutionErrorHandler eeh = (err, ctx) -> {
            errorHandlerInvoked.set(true);
            return ToolErrorHandlerResult.text("handled");
        };

        // Without bypass: error handler is invoked, exception is converted to a tool-result text.
        Map<ToolExecutionRequest, ToolExecutionResult> swallowed = ToolBatchDispatcher.dispatch(baseReq()
                .toolRequests(List.of(req("a", "t", "x")))
                .toolExecutors(exec("t", failer))
                .executionErrorHandler(eeh)
                .build());
        assertThat(errorHandlerInvoked).isTrue();
        assertThat(swallowed.values().iterator().next().resultText()).isEqualTo("handled");

        // With bypass: the marker exception propagates unchanged.
        errorHandlerInvoked.set(false);
        assertThatThrownBy(() -> ToolBatchDispatcher.dispatch(baseReq()
                        .toolRequests(List.of(req("a", "t", "x")))
                        .toolExecutors(exec("t", failer))
                        .executionErrorHandler(eeh)
                        .errorHandlerBypass(GuardrailViolation.class::isInstance)
                        .build()))
                .isInstanceOf(GuardrailViolation.class)
                .hasMessage("denied");
        assertThat(errorHandlerInvoked).isFalse();
    }

    @Test
    void before_and_after_callbacks_fire_for_each_tool() {
        AtomicInteger before = new AtomicInteger();
        AtomicInteger after = new AtomicInteger();
        ToolExecutor te = (request, memId) -> "ok";

        ToolBatchDispatcher.dispatch(baseReq()
                .toolRequests(List.of(req("a", "t", "1"), req("b", "t", "2")))
                .toolExecutors(exec("t", te))
                .beforeToolExecution(b -> before.incrementAndGet())
                .afterToolExecution(b -> after.incrementAndGet())
                .build());

        assertThat(before.get()).isEqualTo(2);
        assertThat(after.get()).isEqualTo(2);
    }

    @Test
    void empty_request_list_returns_empty_map() {
        Map<ToolExecutionRequest, ToolExecutionResult> results = ToolBatchDispatcher.dispatch(
                baseReq().toolRequests(List.of()).build());
        assertThat(results).isEmpty();
    }

    @Test
    void unknown_tool_uses_hallucination_strategy() {
        ToolExecutor te = (request, memId) -> "should-not-run";

        Map<ToolExecutionRequest, ToolExecutionResult> results = ToolBatchDispatcher.dispatch(baseReq()
                .toolRequests(List.of(req("a", "missing", "1")))
                .toolExecutors(exec("present", te))
                .hallucinationStrategy(r ->
                        dev.langchain4j.data.message.ToolExecutionResultMessage.from(r, "hallucinated:" + r.name()))
                .build());

        assertThat(results).hasSize(1);
        assertThat(results.values().iterator().next().resultText()).isEqualTo("hallucinated:missing");
    }
}
