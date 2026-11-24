package dev.langchain4j.agentic.observability;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
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

    public static class FailingOnceRemediation {
        private final AtomicInteger attempts = new AtomicInteger();

        @Agent(async = true, outputKey = "fix")
        public String remediate(@V("decision") String decision) {
            if (attempts.getAndIncrement() == 0) {
                throw new IllegalStateException("sandbox unavailable");
            }
            return "fixed after " + decision;
        }
    }

    /** Holds the root call until the error handler of the asynchronous agent started deciding. */
    public static class ReportAfterErrorHandlerStarted {
        private final CountDownLatch errorHandlerStarted;

        ReportAfterErrorHandlerStarted(CountDownLatch errorHandlerStarted) {
            this.errorHandlerStarted = errorHandlerStarted;
        }

        @Agent(outputKey = "report")
        public String report(@V("decision") String decision) {
            awaitLatch(errorHandlerStarted);
            return "report on " + decision;
        }
    }

    private final ExecutorService singleThread = Executors.newSingleThreadExecutor();
    private final CountDownLatch executorBusy = new CountDownLatch(1);

    @BeforeEach
    void keepTheExecutorBusy() {
        ExecutorProvider.set(() -> singleThread);
        singleThread.submit(() -> awaitLatch(executorBusy));
    }

    @AfterEach
    void releaseTheExecutor() {
        ExecutorProvider.set(null);
        executorBusy.countDown();
        singleThread.shutdownNow();
    }

    private static UntypedAgent analysisThenRemediation(AgentMonitor monitor, Object remediation) {
        return analysisThenRemediation(monitor, remediation, ErrorRecoveryResult::throwException);
    }

    private static UntypedAgent analysisThenRemediation(
            AgentMonitor monitor, Object remediation, Supplier<ErrorRecoveryResult> recovery) {
        UntypedAgent analysis = AgenticServices.sequenceBuilder()
                .name("analysis")
                .subAgents(new Analysis(), remediation)
                .outputKey("decision")
                .build();
        return AgenticServices.sequenceBuilder()
                .subAgents(analysis)
                .outputKey("decision")
                .listener(monitor)
                .errorHandler(errorContext -> recovery.get())
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
        await().atMost(10, SECONDS).until(() -> monitor.successfulExecutions().size() == 1);

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
        await().atMost(10, SECONDS).until(() -> monitor.failedExecutions().size() == 1);

        assertThat(monitor.ongoingExecutions()).isEmpty();
        assertThat(monitor.successfulExecutions()).isEmpty();
        MonitoredExecution execution = monitor.failedExecutions().get(0);
        assertThat(execution.hasError()).isTrue();
        assertThat(execution.error().agentName()).isEqualTo("remediate");
        assertThat(execution.error().error()).hasMessageContaining("remediate");
    }

    @Test
    void recovered_failure_of_a_late_async_subagent_is_recorded_as_successful() {
        AgentMonitor monitor = new AgentMonitor();
        UntypedAgent root = analysisThenRemediation(
                monitor, new FailingRemediation(), () -> ErrorRecoveryResult.result("manual fix"));

        assertThat(root.invoke(Map.of("input", "canary"))).isEqualTo("promote canary");
        assertThat(monitor.ongoingExecutions()).hasSize(1);

        executorBusy.countDown();
        await().atMost(10, SECONDS).until(() -> monitor.ongoingExecutions().isEmpty());

        // the error handler recovered the failure, so the execution succeeded, with the error it reported
        assertThat(monitor.failedExecutions()).isEmpty();
        assertThat(monitor.successfulExecutions()).hasSize(1);
        assertThat(monitor.successfulExecutions().get(0).hasError()).isTrue();
    }

    @Test
    void retried_late_async_subagent_stays_in_one_execution() {
        AgentMonitor monitor = new AgentMonitor();
        UntypedAgent root = analysisThenRemediation(monitor, new FailingOnceRemediation(), ErrorRecoveryResult::retry);

        assertThat(root.invoke(Map.of("input", "canary"))).isEqualTo("promote canary");
        assertThat(monitor.ongoingExecutions()).hasSize(1);

        executorBusy.countDown();
        await().atMost(10, SECONDS).until(() -> monitor.ongoingExecutions().isEmpty());

        // the retry is a second invocation of the agent nested under the same completed parent
        assertThat(monitor.failedExecutions()).isEmpty();
        assertThat(monitor.successfulExecutions()).hasSize(1);
        AgentInvocation analysis = monitor.successfulExecutions()
                .get(0)
                .topLevelInvocations()
                .nestedInvocations()
                .get(0);
        assertThat(analysis.nestedInvocations())
                .extracting(invocation -> invocation.agent().name())
                .containsExactly("analyze", "remediate", "remediate");
        AgentInvocation retry = analysis.nestedInvocations().get(2);
        assertThat(retry.done()).isTrue();
        assertThat(retry.output()).isEqualTo("fixed after promote canary");
    }

    @Test
    void unrecovered_failure_of_an_async_subagent_before_the_end_of_the_root_call_is_recorded_as_failed() {
        // an executor running the agent inline makes it fail at its submission, before the root call ends
        ExecutorProvider.set(() -> Runnable::run);
        AgentMonitor monitor = new AgentMonitor();
        UntypedAgent root = analysisThenRemediation(monitor, new FailingRemediation());

        assertThat(root.invoke(Map.of("input", "canary"))).isEqualTo("promote canary");

        // the same outcome as when the agent fails after the end of the root call
        assertThat(monitor.ongoingExecutions()).isEmpty();
        assertThat(monitor.successfulExecutions()).isEmpty();
        assertThat(monitor.failedExecutions()).hasSize(1);
        assertThat(monitor.failedExecutions().get(0).error().agentName()).isEqualTo("remediate");
    }

    /**
     * The asynchronous remediation fails before the end of the root call, while its error handler decides only after
     * the root call returned.
     */
    private static UntypedAgent remediationRecoveredAfterTheRootCall(
            AgentMonitor monitor, CountDownLatch rootReturned, Supplier<ErrorRecoveryResult> recovery) {
        CountDownLatch errorHandlerStarted = new CountDownLatch(1);
        return AgenticServices.sequenceBuilder()
                .subAgents(
                        new Analysis(),
                        new FailingOnceRemediation(),
                        new ReportAfterErrorHandlerStarted(errorHandlerStarted))
                .outputKey("decision")
                .listener(monitor)
                .errorHandler(errorContext -> {
                    errorHandlerStarted.countDown();
                    awaitLatch(rootReturned);
                    return recovery.get();
                })
                .build();
    }

    @Test
    void unrecovered_failure_of_an_async_subagent_decided_after_the_end_of_the_root_call_is_recorded_as_failed() {
        executorBusy.countDown();
        AgentMonitor monitor = new AgentMonitor();
        CountDownLatch rootReturned = new CountDownLatch(1);
        UntypedAgent root =
                remediationRecoveredAfterTheRootCall(monitor, rootReturned, ErrorRecoveryResult::throwException);

        assertThat(root.invoke(Map.of("input", "canary"))).isEqualTo("promote canary");
        // the execution stays ongoing while the error handler decides
        assertThat(monitor.ongoingExecutions()).hasSize(1);

        rootReturned.countDown();
        await().atMost(10, SECONDS).until(() -> monitor.ongoingExecutions().isEmpty());

        assertThat(monitor.successfulExecutions()).isEmpty();
        assertThat(monitor.failedExecutions()).hasSize(1);
        assertThat(monitor.failedExecutions().get(0).error().agentName()).isEqualTo("remediate");
    }

    @Test
    void retry_of_an_async_subagent_decided_after_the_end_of_the_root_call_stays_in_one_execution() {
        executorBusy.countDown();
        AgentMonitor monitor = new AgentMonitor();
        CountDownLatch rootReturned = new CountDownLatch(1);
        UntypedAgent root = remediationRecoveredAfterTheRootCall(monitor, rootReturned, ErrorRecoveryResult::retry);

        assertThat(root.invoke(Map.of("input", "canary"))).isEqualTo("promote canary");
        assertThat(monitor.ongoingExecutions()).hasSize(1);

        rootReturned.countDown();
        await().atMost(10, SECONDS).until(() -> monitor.ongoingExecutions().isEmpty());

        assertThat(monitor.failedExecutions()).isEmpty();
        assertThat(monitor.successfulExecutions()).hasSize(1);
        assertThat(monitor.successfulExecutions().get(0).topLevelInvocations().nestedInvocations())
                .extracting(invocation -> invocation.agent().name())
                .containsExactly("analyze", "remediate", "report", "remediate");
    }

    private static void awaitLatch(CountDownLatch latch) {
        try {
            latch.await(10, SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
