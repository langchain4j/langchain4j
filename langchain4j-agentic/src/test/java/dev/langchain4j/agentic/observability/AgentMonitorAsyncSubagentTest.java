package dev.langchain4j.agentic.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.agent.AgentInvocationException;
import dev.langchain4j.service.V;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/**
 * An async sub-agent runs on the executor, so it can start after its parent, or even the whole root, has completed.
 * The monitor attached to the root must still record it in the execution of that root.
 */
class AgentMonitorAsyncSubagentTest {

    @Test
    void async_subagent_starting_after_its_parent_completed_is_recorded_in_the_root_execution() {
        SlowExecutorMonitor monitor = new SlowExecutorMonitor();
        UntypedAgent desk = desk(monitor, new AudienceReviewer());

        desk.invoke(Map.of("story", "a story about dragons"));

        assertThat(monitor.failures).isEmpty();
        assertThat(monitor.ongoingExecutions()).isEmpty();
        assertThat(monitor.successfulExecutions()).hasSize(1);
        AgentInvocation root = monitor.successfulExecutions().get(0).topLevelInvocations();
        assertThat(root.agent().name()).isEqualTo("desk");
        AgentInvocation reviews = root.nestedInvocations().get(0);
        assertThat(nestedNames(reviews)).containsExactlyInAnyOrder("reviewAudience", "checkLength");

        // the root is reported as completed only after its async sub-agent
        AgentInvocation review = nested(reviews, "reviewAudience");
        assertThat(review.done()).isTrue();
        assertThat(root.finishTime()).isAfterOrEqualTo(review.finishTime());
    }

    @Test
    void failing_async_subagent_starting_after_its_parent_completed_fails_the_root_execution() {
        SlowExecutorMonitor monitor = new SlowExecutorMonitor();
        UntypedAgent desk = desk(monitor, new FailingAudienceReviewer());

        assertThatThrownBy(() -> desk.invoke(Map.of("story", "a story about dragons")))
                .isInstanceOf(AgentInvocationException.class)
                .hasRootCauseMessage("audience review failed");

        assertThat(monitor.failures).isEmpty();
        assertThat(monitor.ongoingExecutions()).isEmpty();
        assertThat(monitor.successfulExecutions()).isEmpty();
        assertThat(monitor.failedExecutions()).hasSize(1);
        MonitoredExecution execution = monitor.failedExecutions().get(0);
        assertThat(execution.topLevelInvocations().agent().name()).isEqualTo("desk");
        assertThat(execution.error().agentName()).isEqualTo("reviewAudience");
        AgentInvocation reviews =
                execution.topLevelInvocations().nestedInvocations().get(0);
        assertThat(nestedNames(reviews)).containsExactlyInAnyOrder("reviewAudience", "checkLength");
    }

    private static UntypedAgent desk(SlowExecutorMonitor monitor, Object audienceReviewer) {
        UntypedAgent reviews = AgenticServices.parallelBuilder()
                .name("reviews")
                .subAgents(audienceReviewer, new LengthChecker())
                .build();
        return AgenticServices.sequenceBuilder()
                .name("desk")
                .subAgents(reviews, new Publisher())
                .outputKey("published")
                .listener(monitor)
                .build();
    }

    public static class AudienceReviewer {

        @Agent(async = true, outputKey = "audienceReview")
        public String reviewAudience(@V("story") String story) {
            return "fine for kids";
        }
    }

    public static class FailingAudienceReviewer {

        @Agent(async = true, outputKey = "audienceReview")
        public String reviewAudience(@V("story") String story) {
            throw new IllegalStateException("audience review failed");
        }
    }

    public static class LengthChecker {

        @Agent(outputKey = "wordCount")
        public int checkLength(@V("story") String story) {
            return story.split(" ").length;
        }
    }

    public static class Publisher {

        @Agent(outputKey = "published")
        public String publish(@V("story") String story) {
            return "Published: " + story;
        }
    }

    /**
     * Holds the async agent back, as a busy executor would do, until its parallel parent has completed and the root
     * is waiting for its pending responses.
     */
    static class SlowExecutorMonitor extends AgentMonitor {

        final CountDownLatch parentCompleted = new CountDownLatch(1);
        final List<RuntimeException> failures = new CopyOnWriteArrayList<>();
        volatile Thread rootThread;

        @Override
        public void beforeAgentInvocation(AgentRequest agentRequest) {
            if ("desk".equals(agentRequest.agentName())) {
                rootThread = Thread.currentThread();
            }
            if ("reviewAudience".equals(agentRequest.agentName())) {
                awaitParentCompleted();
                awaitRootWaiting();
            }
            try {
                super.beforeAgentInvocation(agentRequest);
            } catch (RuntimeException e) {
                failures.add(e);
                throw e;
            }
        }

        @Override
        public void afterAgentInvocation(AgentResponse agentResponse) {
            try {
                super.afterAgentInvocation(agentResponse);
            } catch (RuntimeException e) {
                failures.add(e);
                throw e;
            }
            if ("reviews".equals(agentResponse.agentName())) {
                parentCompleted.countDown();
            }
        }

        private void awaitParentCompleted() {
            try {
                if (!parentCompleted.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("parent of the async agent did not complete");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }

        /**
         * The root thread is parked only when it waits for the response of the async agent, so from this point on
         * everything but the async agent has completed: without this wait, a monitor resolving the completed parent
         * could still pass by chance, with the async agent starting before the root has been finalized.
         */
        private void awaitRootWaiting() {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (rootThread.getState() != Thread.State.WAITING) {
                if (System.nanoTime() > deadline) {
                    throw new IllegalStateException("root did not wait for the async agent");
                }
                Thread.onSpinWait();
            }
        }
    }

    private static List<String> nestedNames(AgentInvocation invocation) {
        List<String> names = new ArrayList<>();
        for (AgentInvocation nested : invocation.nestedInvocations()) {
            names.add(nested.agent().name());
        }
        return names;
    }

    private static AgentInvocation nested(AgentInvocation invocation, String agentName) {
        for (AgentInvocation nested : invocation.nestedInvocations()) {
            if (agentName.equals(nested.agent().name())) {
                return nested;
            }
        }
        throw new AssertionError(
                agentName + " not invoked under " + invocation.agent().name());
    }
}
