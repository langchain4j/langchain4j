package dev.langchain4j.agentic.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.scope.AgenticScope;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class AgentMonitorTest {

    @Test
    void should_handle_concurrent_invocations_without_throwing_exception() throws InterruptedException {
        // given
        AgentMonitor monitor = new AgentMonitor();
        Object memoryId = "concurrent-memory-id";

        AgenticScope scope = mock(AgenticScope.class);
        when(scope.memoryId()).thenReturn(memoryId);

        AgentInstance agent = mock(AgentInstance.class);
        when(agent.agentId()).thenReturn("agent-1");
        when(agent.name()).thenReturn("test-agent");

        int numThreads = 10;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch latch = new CountDownLatch(numThreads);
        AtomicInteger failureCount = new AtomicInteger(0);

        // when
        for (int i = 0; i < numThreads; i++) {
            executor.submit(() -> {
                try {
                    AgentRequest request = new AgentRequest(scope, agent, new HashMap<>());
                    monitor.beforeAgentInvocation(request);
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                    e.printStackTrace();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        // then
        assertThat(failureCount.get()).isEqualTo(0);
        assertThat(monitor.ongoingExecutions()).containsKey(memoryId);
    }

    @Test
    void should_prevent_concurrent_modification_exception_during_streaming() throws InterruptedException {
        // given
        AgentMonitor monitor = new AgentMonitor();
        Object memoryId = "concurrency-streaming-id";

        AgenticScope scope = mock(AgenticScope.class);
        when(scope.memoryId()).thenReturn(memoryId);

        AgentInstance agent = mock(AgentInstance.class);
        when(agent.agentId()).thenReturn("agent-1");
        when(agent.name()).thenReturn("test-agent");

        AgentRequest request = new AgentRequest(scope, agent, new HashMap<>());
        monitor.beforeAgentInvocation(request);

        int numThreads = 20;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch latch = new CountDownLatch(numThreads);
        AtomicInteger exceptionCount = new AtomicInteger(0);

        // when - concurrently adding executions and reading them via successfulExecutions()
        for (int i = 0; i < numThreads; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    if (index % 2 == 0) {
                        // simulate completion
                        AgentResponse response = new AgentResponse(scope, agent, new HashMap<>(), "output");
                        monitor.afterAgentInvocation(response);
                        
                        // re-invoke to put back in ongoing
                        monitor.beforeAgentInvocation(request);
                    } else {
                        // concurrently read successful executions (which previously triggered ConcurrentModificationException)
                        List<MonitoredExecution> list = monitor.successfulExecutions();
                        assertThat(list).isNotNull();
                    }
                } catch (Exception e) {
                    exceptionCount.incrementAndGet();
                    e.printStackTrace();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        // then
        assertThat(exceptionCount.get()).isEqualTo(0);
    }
}
