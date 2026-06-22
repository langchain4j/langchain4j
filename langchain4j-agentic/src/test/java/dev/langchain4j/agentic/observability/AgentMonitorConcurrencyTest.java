package dev.langchain4j.agentic.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.planner.AgenticSystemTopology;
import dev.langchain4j.agentic.scope.AgenticScope;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/**
 * Concurrency tests for {@link AgentMonitor} verifying that it can be safely shared across
 * threads. The patterns exercised here are produced by the agentic module's parallel and
 * concurrent topologies, where the same monitor instance is invoked from multiple worker
 * threads.
 */
class AgentMonitorConcurrencyTest {

    private static final int THREADS = 4;
    private static final int RUNS = 10;

    @Test
    void concurrent_invocations_with_distinct_memory_ids_are_all_tracked() throws Exception {
        AgentMonitor monitor = new AgentMonitor();

        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < RUNS; i++) {
                final int idx = i;
                futures.add(pool.submit(() -> {
                    Object memoryId = "memory-" + idx;
                    AgentInstance agent = stubAgent("root-" + idx, null);
                    AgenticScope scope = stubScope(memoryId);
                    AgentRequest request = new AgentRequest(scope, agent, Map.of());
                    AgentResponse response = new AgentResponse(scope, agent, Map.of(), "ok", null, null);
                    awaitStart(start);
                    monitor.beforeAgentInvocation(request);
                    monitor.afterAgentInvocation(response);
                }));
            }

            start.countDown();
            for (Future<?> future : futures) {
                future.get(30, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(monitor.allMemoryIds()).hasSize(RUNS);
        assertThat(monitor.successfulExecutions()).hasSize(RUNS);
        assertThat(monitor.ongoingExecutions()).isEmpty();
    }

    @Test
    void concurrent_parallel_sub_agent_invocations_are_all_recorded() throws Exception {
        AgentMonitor monitor = new AgentMonitor();

        Object memoryId = "memory-parallel";
        AgenticScope scope = stubScope(memoryId);

        AgentInstance root = stubAgent("root", null);

        // Set up the root invocation first, single-threaded — mirrors how the agentic
        // framework opens a top-level invocation before fanning out sub-agents.
        AgentRequest rootRequest = new AgentRequest(scope, root, Map.of());
        monitor.beforeAgentInvocation(rootRequest);

        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        int subAgents = RUNS;
        try {
            for (int i = 0; i < subAgents; i++) {
                AgentInstance sub = stubAgent("sub-" + i, root);
                AgentRequest subRequest = new AgentRequest(scope, sub, Map.of());
                AgentResponse subResponse = new AgentResponse(scope, sub, Map.of(), "ok", null, null);
                futures.add(pool.submit(() -> {
                    awaitStart(start);
                    monitor.beforeAgentInvocation(subRequest);
                    monitor.afterAgentInvocation(subResponse);
                }));
            }

            start.countDown();
            for (Future<?> future : futures) {
                future.get(30, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        AgentResponse rootResponse = new AgentResponse(scope, root, Map.of(), "ok", null, null);
        monitor.afterAgentInvocation(rootResponse);

        List<MonitoredExecution> successful = monitor.successfulExecutionsFor(memoryId);
        assertThat(successful).hasSize(1).doesNotContainNull();
        MonitoredExecution execution = successful.get(0);
        assertThat(execution.topLevelInvocations().nestedInvocations()).hasSize(subAgents);
        assertThat(execution.ongoingInvocations()).isEmpty();
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
