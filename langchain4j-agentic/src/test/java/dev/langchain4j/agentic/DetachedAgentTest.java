package dev.langchain4j.agentic;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agentic.observability.AgentInvocation;
import dev.langchain4j.agentic.observability.AgentInvocationError;
import dev.langchain4j.agentic.observability.AgentListener;
import dev.langchain4j.agentic.observability.AgentMonitor;
import dev.langchain4j.agentic.observability.AgentRequest;
import dev.langchain4j.agentic.observability.AgentResponse;
import dev.langchain4j.agentic.observability.MonitoredExecution;
import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.model.chat.mock.ChatModelMock;
import dev.langchain4j.model.decision.mock.DecisionModelMock;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import dev.langchain4j.service.decision.Decide;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/**
 * A detached agent runs in the background: the root returns without waiting for it, and the scope stays alive until
 * the detached agent completes.
 */
class DetachedAgentTest {

    @Test
    void root_returns_without_waiting_for_its_detached_subagent() throws InterruptedException {
        Recorder recorder = new Recorder();
        Remediator remediator = new Remediator();
        UntypedAgent rollout = rollout(remediator, recorder);

        Object decision = rollout.invoke(Map.of("metrics", "error rate 12%"));

        assertThat(decision).isEqualTo("rollback");
        assertThat(recorder.events).doesNotContain("after:remediate", "destroyed");

        remediator.proceed.countDown();

        assertThat(recorder.next()).isEqualTo("after:remediate");
        assertThat(recorder.next()).isEqualTo("destroyed");
        assertThat(recorder.remediation).isEqualTo("PR opened for: error rate 12%");
        assertThat(recorder.destroyedScope.readState("remediation")).isEqualTo("PR opened for: error rate 12%");
    }

    @Test
    void failing_detached_subagent_does_not_fail_the_root() throws InterruptedException {
        Recorder recorder = new Recorder();
        FailingRemediator remediator = new FailingRemediator();
        UntypedAgent rollout = rollout(remediator, recorder);

        Object decision = rollout.invoke(Map.of("metrics", "error rate 12%"));

        assertThat(decision).isEqualTo("rollback");

        remediator.proceed.countDown();

        assertThat(recorder.next()).isEqualTo("error:remediate");
        assertThat(recorder.next()).isEqualTo("destroyed");
        assertThat(recorder.destroyedScope.hasState("remediation")).isFalse();
    }

    @Test
    void monitor_records_a_detached_subagent_starting_after_the_root_returned() {
        BusyExecutorMonitor monitor = new BusyExecutorMonitor();
        UntypedAgent rollout = rollout(new Remediator(true), monitor);

        assertThat(rollout.invoke(Map.of("metrics", "error rate 12%"))).isEqualTo("rollback");

        assertThat(monitor.successfulExecutions()).isEmpty();
        assertThat(monitor.ongoingExecutions()).hasSize(1);

        monitor.rootReturned.countDown();

        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (!monitor.ongoingExecutions().isEmpty() && System.nanoTime() < deadline) {
            Thread.onSpinWait();
        }
        assertThat(monitor.ongoingExecutions()).isEmpty();
        assertThat(monitor.successfulExecutions()).hasSize(1);
        MonitoredExecution execution = monitor.successfulExecutions().get(0);
        AgentInvocation root = execution.topLevelInvocations();
        assertThat(root.agent().name()).isEqualTo("rollout");
        assertThat(nestedNames(root)).containsExactly("analyze", "remediate");
        assertThat(root.nestedInvocations().get(1).done()).isTrue();
    }

    @Test
    void every_kind_of_agent_can_be_detached() {
        ChatModelMock chatModel = ChatModelMock.thatAlwaysResponds("PR opened");
        UntypedAgent rollout = AgenticServices.sequenceBuilder()
                .subAgents(
                        AgenticServices.agentBuilder(RemediationAgent.class)
                                .chatModel(chatModel)
                                .build(),
                        AgenticServices.agentBuilder(ReviewAgent.class)
                                .chatModel(chatModel)
                                .detached(true)
                                .build(),
                        AgenticServices.nonAiAgentBuilder(
                                        new AgenticServices.AgenticScopeFunction.NonThrowingFunction<
                                                AgenticScope, String>() {
                                            @Override
                                            public String apply(AgenticScope agenticScope) {
                                                return "notified";
                                            }
                                        })
                                .outputKey("notification")
                                .detached(true)
                                .build(),
                        AgenticServices.decisionAgentBuilder(RemediationRouter.class)
                                .decisionModel(DecisionModelMock.thatAlwaysThrowsException())
                                .build(),
                        new Remediator())
                .build();

        List<AgentInstance> subagents = ((AgentInstance) rollout).subagents();

        assertThat(subagents).hasSize(5);
        for (AgentInstance subagent : subagents) {
            assertThat(subagent.detached()).as(subagent.name()).isTrue();
        }
    }

    public interface RemediationAgent {

        @UserMessage("Open a pull request fixing {{metrics}}")
        @Agent(detached = true, outputKey = "remediation")
        String remediate(@V("metrics") String metrics);
    }

    public interface ReviewAgent {

        @UserMessage("Review the fix for {{metrics}}")
        @Agent(outputKey = "review")
        String review(@V("metrics") String metrics);
    }

    public enum Remediation {
        ROLLBACK,
        PATCH
    }

    public interface RemediationRouter {

        @Decide("Which remediation fits these metrics?")
        @Agent(detached = true, outputKey = "remediationKind")
        Remediation route(@V("metrics") String metrics);
    }

    private static UntypedAgent rollout(Object remediator, AgentListener listener) {
        return AgenticServices.sequenceBuilder()
                .name("rollout")
                .subAgents(new Analyst(), remediator)
                .outputKey("decision")
                .listener(listener)
                .build();
    }

    public static class Analyst {

        @Agent(outputKey = "decision")
        public String analyze(@V("metrics") String metrics) {
            return "rollback";
        }
    }

    public static class Remediator {

        final CountDownLatch proceed;

        Remediator() {
            this(false);
        }

        Remediator(boolean released) {
            this.proceed = new CountDownLatch(released ? 0 : 1);
        }

        @Agent(detached = true, outputKey = "remediation")
        public String remediate(@V("metrics") String metrics) {
            await(proceed);
            return "PR opened for: " + metrics;
        }
    }

    public static class FailingRemediator {

        final CountDownLatch proceed = new CountDownLatch(1);

        @Agent(detached = true, outputKey = "remediation")
        public String remediate(@V("metrics") String metrics) {
            await(proceed);
            throw new IllegalStateException("remediation failed");
        }
    }

    static class Recorder implements AgentListener {

        final BlockingQueue<String> events = new LinkedBlockingQueue<>();
        volatile Object remediation;
        volatile AgenticScope destroyedScope;

        @Override
        public void afterAgentInvocation(AgentResponse agentResponse) {
            if ("remediate".equals(agentResponse.agentName())) {
                remediation = agentResponse.output();
                events.add("after:remediate");
            }
        }

        @Override
        public void onAgentInvocationError(AgentInvocationError agentInvocationError) {
            if ("remediate".equals(agentInvocationError.agentName())) {
                events.add("error:remediate");
            }
        }

        @Override
        public void beforeAgenticScopeDestroyed(AgenticScope agenticScope) {
            destroyedScope = agenticScope;
            events.add("destroyed");
        }

        @Override
        public boolean inheritedBySubagents() {
            return true;
        }

        String next() throws InterruptedException {
            String event = events.poll(10, TimeUnit.SECONDS);
            assertThat(event).as("next listener event").isNotNull();
            return event;
        }
    }

    /**
     * Holds the detached agent back, as a busy executor would do, until the root has returned.
     */
    static class BusyExecutorMonitor extends AgentMonitor {

        final CountDownLatch rootReturned = new CountDownLatch(1);

        @Override
        public void beforeAgentInvocation(AgentRequest agentRequest) {
            if ("remediate".equals(agentRequest.agentName())) {
                await(rootReturned);
            }
            super.beforeAgentInvocation(agentRequest);
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("latch not released");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private static List<String> nestedNames(AgentInvocation invocation) {
        List<String> names = new ArrayList<>();
        for (AgentInvocation nested : invocation.nestedInvocations()) {
            names.add(nested.agent().name());
        }
        return names;
    }
}
