package dev.langchain4j.agentic.observability;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.agent.ErrorRecoveryResult;
import dev.langchain4j.service.V;
import dev.langchain4j.spi.ExecutorProvider;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * An asynchronous sub-agent that its executor starts only after the sequence invoking it completed, and after the
 * root call returned, is still nested under that sequence, and the execution is recorded only when it completes.
 */
class AgentMonitorAsyncSubagentTest {

    public static class Analysis {
        @Agent(outputKey = "decision")
        public String analyze(@V("input") String input) {
            return "promote " + input;
        }
    }

    public static class Remediation {
        @Agent(async = true, outputKey = "fix")
        public String remediate(@V("decision") String decision) {
            return "fixed after " + decision;
        }
    }

    public static class FailingRemediation {
        @Agent(async = true, outputKey = "fix")
        public String remediate(@V("decision") String decision) {
            throw new IllegalStateException("sandbox unavailable");
        }
    }

    private final ExecutorService singleThread = Executors.newSingleThreadExecutor();
    private final CountDownLatch executorBusy = new CountDownLatch(1);

    @BeforeEach
    void keepTheExecutorBusy() {
        ExecutorProvider.set(() -> singleThread);
        singleThread.submit(() -> await(executorBusy));
    }

    @AfterEach
    void releaseTheExecutor() {
        ExecutorProvider.set(null);
        executorBusy.countDown();
        singleThread.shutdownNow();
    }

    private static UntypedAgent analysisThenRemediation(AgentMonitor monitor, Object remediation) {
        UntypedAgent analysis = AgenticServices.sequenceBuilder()
                .name("analysis")
                .subAgents(new Analysis(), remediation)
                .outputKey("decision")
                .build();
        return AgenticServices.sequenceBuilder()
                .subAgents(analysis)
                .outputKey("decision")
                .listener(monitor)
                .errorHandler(errorContext -> ErrorRecoveryResult.throwException())
                .build();
    }

    @Test
    void late_async_subagent_is_nested_under_its_completed_parent_and_ends_the_execution() {
        AgentMonitor monitor = new AgentMonitor();
        UntypedAgent root = analysisThenRemediation(monitor, new Remediation());

        assertThat(root.invoke(Map.of("input", "canary"))).isEqualTo("promote canary");

        // the root returned, the sub-agent is submitted but not started, and the execution is still ongoing
        assertThat(monitor.successfulExecutions()).isEmpty();
        assertThat(monitor.ongoingExecutions()).hasSize(1);
        MonitoredExecution execution =
                monitor.ongoingExecutions().values().iterator().next();
        assertThat(execution.done()).isTrue();
        assertThat(execution.ongoingInvocations())
                .extracting(invocation -> invocation.agent().name())
                .containsExactly("remediate");

        executorBusy.countDown();
        awaitUntil(() -> monitor.successfulExecutions().size() == 1);

        assertThat(monitor.ongoingExecutions()).isEmpty();
        AgentInvocation analysis =
                execution.topLevelInvocations().nestedInvocations().get(0);
        assertThat(analysis.agent().name()).isEqualTo("analysis");
        assertThat(analysis.done()).isTrue();
        assertThat(analysis.nestedInvocations())
                .extracting(invocation -> invocation.agent().name())
                .containsExactly("analyze", "remediate");
        AgentInvocation remediation = analysis.nestedInvocations().get(1);
        assertThat(remediation.done()).isTrue();
        assertThat(remediation.output()).isEqualTo("fixed after promote canary");
    }

    @Test
    void failure_of_a_late_async_subagent_records_a_failed_execution() {
        AgentMonitor monitor = new AgentMonitor();
        UntypedAgent root = analysisThenRemediation(monitor, new FailingRemediation());

        assertThat(root.invoke(Map.of("input", "canary"))).isEqualTo("promote canary");
        assertThat(monitor.ongoingExecutions()).hasSize(1);

        executorBusy.countDown();
        awaitUntil(() -> monitor.failedExecutions().size() == 1);

        assertThat(monitor.ongoingExecutions()).isEmpty();
        assertThat(monitor.successfulExecutions()).isEmpty();
        MonitoredExecution execution = monitor.failedExecutions().get(0);
        assertThat(execution.hasError()).isTrue();
        assertThat(execution.error().agentName()).isEqualTo("remediate");
        assertThat(execution.error().error()).hasMessageContaining("remediate");
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(10, SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void awaitUntil(BooleanSupplier condition) {
        long deadline = System.currentTimeMillis() + 10_000;
        while (!condition.getAsBoolean()) {
            assertThat(System.currentTimeMillis()).as("condition met in time").isLessThan(deadline);
            Thread.onSpinWait();
        }
    }
}
