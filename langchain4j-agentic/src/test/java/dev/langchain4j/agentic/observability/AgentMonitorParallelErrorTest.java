package dev.langchain4j.agentic.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.planner.AgenticSystemTopology;
import dev.langchain4j.agentic.scope.AgenticScope;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/**
 * Verifies that {@link AgentMonitor#onAgentInvocationError} does not remove the
 * {@link MonitoredExecution} from {@code ongoingExecutions} while parallel sibling
 * sub-agents are still running. The erroring agent is removed from the execution's
 * {@code ongoingInvocations}, but the execution itself stays available until all
 * agents complete.
 */
class AgentMonitorParallelErrorTest {

    /**
     * When one parallel sub-agent errors, sibling sub-agents that complete afterwards
     * must still find the shared {@link MonitoredExecution} in {@code ongoingExecutions}.
     */
    @Test
    void parallel_sub_agent_error_should_not_cause_npe_for_sibling() {
        AgentMonitor monitor = new AgentMonitor();

        Object memoryId = "memory-parallel-error";
        AgenticScope scope = stubScope(memoryId);

        AgentInstance root = stubAgent("root", null);
        AgentInstance subA = stubAgent("sub-a", root);
        AgentInstance subB = stubAgent("sub-b", root);

        // 1. Root agent starts
        monitor.beforeAgentInvocation(new AgentRequest(scope, root, Map.of()));

        // 2. Both sub-agents start (as a parallel agent would do)
        monitor.beforeAgentInvocation(new AgentRequest(scope, subA, Map.of()));
        monitor.beforeAgentInvocation(new AgentRequest(scope, subB, Map.of()));

        // 3. Sub-agent A errors
        monitor.onAgentInvocationError(
                new AgentInvocationError(scope, subA, Map.of(), new RuntimeException("timeout")));

        // 4. Sub-agent B completes successfully — this should NOT throw NPE
        assertThatNoException().isThrownBy(() ->
                monitor.afterAgentInvocation(new AgentResponse(scope, subB, Map.of(), "ok", null, null)));
    }

    /**
     * Same scenario exercised with concurrent threads to verify thread-safety of the
     * error and success paths when they overlap.
     */
    @Test
    void concurrent_parallel_sub_agent_error_should_not_cause_npe_for_sibling() throws Exception {
        AgentMonitor monitor = new AgentMonitor();

        Object memoryId = "memory-concurrent-error";
        AgenticScope scope = stubScope(memoryId);

        AgentInstance root = stubAgent("root", null);
        AgentInstance subA = stubAgent("sub-a", root);
        AgentInstance subB = stubAgent("sub-b", root);

        // Root and sub-agents register
        monitor.beforeAgentInvocation(new AgentRequest(scope, root, Map.of()));
        monitor.beforeAgentInvocation(new AgentRequest(scope, subA, Map.of()));
        monitor.beforeAgentInvocation(new AgentRequest(scope, subB, Map.of()));

        // Sub-agent A errors and sub-agent B succeeds concurrently
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> errorFuture = pool.submit(() -> {
                awaitStart(start);
                monitor.onAgentInvocationError(
                        new AgentInvocationError(scope, subA, Map.of(), new RuntimeException("timeout")));
            });

            Future<?> successFuture = pool.submit(() -> {
                awaitStart(start);
                monitor.afterAgentInvocation(
                        new AgentResponse(scope, subB, Map.of(), "ok", null, null));
            });

            start.countDown();

            // Both should complete without exception
            errorFuture.get(10, TimeUnit.SECONDS);
            successFuture.get(10, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }
    }

    /**
     * After one sub-agent errors, the root agent should still be able to complete
     * the execution lifecycle (e.g., the parent catches the error and finishes).
     */
    @Test
    void root_agent_can_complete_after_sub_agent_error() {
        AgentMonitor monitor = new AgentMonitor();

        Object memoryId = "memory-root-after-error";
        AgenticScope scope = stubScope(memoryId);

        AgentInstance root = stubAgent("root", null);
        AgentInstance subA = stubAgent("sub-a", root);
        AgentInstance subB = stubAgent("sub-b", root);

        monitor.beforeAgentInvocation(new AgentRequest(scope, root, Map.of()));
        monitor.beforeAgentInvocation(new AgentRequest(scope, subA, Map.of()));
        monitor.beforeAgentInvocation(new AgentRequest(scope, subB, Map.of()));

        // Sub-agent A errors
        monitor.onAgentInvocationError(
                new AgentInvocationError(scope, subA, Map.of(), new RuntimeException("timeout")));

        // Sub-agent B succeeds
        assertThatNoException().isThrownBy(() ->
                monitor.afterAgentInvocation(new AgentResponse(scope, subB, Map.of(), "ok", null, null)));

        // Root agent finishes — should not throw
        assertThatNoException().isThrownBy(() ->
                monitor.afterAgentInvocation(new AgentResponse(scope, root, Map.of(), "result", null, null)));

        // The execution should be done and tracked (either as failed or successful)
        assertThat(monitor.ongoingExecutionFor(memoryId)).isNull();
    }

    private static void awaitStart(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }

    private static AgentInstance stubAgent(String agentId, AgentInstance parent) {
        AgentInstance agent = mock(AgentInstance.class);
        when(agent.agentId()).thenReturn(agentId);
        when(agent.name()).thenReturn(agentId);
        when(agent.parent()).thenReturn(parent);
        when(agent.topology()).thenReturn(AgenticSystemTopology.SEQUENCE);
        return agent;
    }

    private static AgenticScope stubScope(Object memoryId) {
        AgenticScope scope = mock(AgenticScope.class);
        when(scope.memoryId()).thenReturn(memoryId);
        return scope;
    }
}
