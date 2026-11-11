package dev.langchain4j.agentic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import dev.langchain4j.agentic.agent.ErrorRecoveryResult;
import dev.langchain4j.agentic.planner.Action;
import dev.langchain4j.agentic.planner.AgentArgument;
import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.planner.InitPlanningContext;
import dev.langchain4j.agentic.planner.Planner;
import dev.langchain4j.agentic.planner.PlanningContext;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;
import org.junit.jupiter.api.Test;

class AsyncAgentRetryTest {

    private final List<String> invoked = new CopyOnWriteArrayList<>();
    private final AtomicInteger attempts = new AtomicInteger();

    /** An asynchronous agent failing at its first attempt and answering "tracked" at the next ones. */
    private Object asyncTrackerFailingOnce() {
        return AgenticServices.<Object>nonAiAgentBuilder(scope -> {
                    invoked.add("tracker");
                    LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(100));
                    if (attempts.incrementAndGet() == 1) {
                        throw new IllegalStateException("tracker temporarily down");
                    }
                    return "tracked";
                })
                .name("tracker")
                .inputs(new AgentArgument(String.class, "ticket"))
                .outputKey("status")
                .async(true)
                .build();
    }

    private Object writer() {
        return AgenticServices.<Object>nonAiAgentBuilder(scope -> {
                    invoked.add("writer");
                    return "reply after " + scope.readState("status");
                })
                .name("writer")
                .inputs(new AgentArgument(String.class, "ticket"))
                .outputKey("reply")
                .build();
    }

    /**
     * Invokes the tracker, then the writer, reading the output of the tracker while deciding the next action, like the
     * planners that send the outputs of the subagents to a decision model.
     */
    private static class ReadingPlanner implements Planner {

        private final List<Integer> notifications;
        private List<AgentInstance> subagents;

        ReadingPlanner(List<Integer> notifications) {
            this.notifications = notifications;
        }

        @Override
        public void init(InitPlanningContext initPlanningContext) {
            this.subagents = initPlanningContext.subagents();
        }

        @Override
        public Action firstAction(PlanningContext planningContext) {
            return call(subagents.get(0));
        }

        @Override
        public Action nextAction(PlanningContext planningContext) {
            notifications.add(1);
            if (planningContext.previousAgentInvocation().agentName().equals("writer")) {
                return done();
            }
            // waits for the output of the asynchronous tracker
            planningContext.agenticScope().readState("status");
            return call(subagents.get(1));
        }
    }

    @Test
    void retried_async_agent_notifies_its_planner_only_once() {
        List<Integer> notifications = new CopyOnWriteArrayList<>();
        UntypedAgent system = AgenticServices.plannerBuilder()
                .subAgents(asyncTrackerFailingOnce(), writer())
                .outputKey("reply")
                .errorHandler(errorContext -> ErrorRecoveryResult.retry())
                .planner(() -> new ReadingPlanner(notifications))
                .build();

        Object reply = assertTimeoutPreemptively(Duration.ofSeconds(10), () -> system.invoke(Map.of("ticket", "t")));

        assertThat(reply).isEqualTo("reply after tracked");
        assertThat(invoked).containsExactly("tracker", "tracker", "writer");
        // once for the tracker, whose retry completes its pending answer, and once for the writer
        assertThat(notifications).hasSize(2);
    }

    @Test
    void retried_async_agent_completes_a_sequence() {
        UntypedAgent sequence = AgenticServices.sequenceBuilder()
                .subAgents(asyncTrackerFailingOnce(), writer())
                .outputKey("reply")
                .errorHandler(errorContext -> ErrorRecoveryResult.retry())
                .build();

        Object reply = assertTimeoutPreemptively(Duration.ofSeconds(10), () -> sequence.invoke(Map.of("ticket", "t")));

        assertThat(reply).isEqualTo("reply after tracked");
        // the writer starts while the tracker runs, and waits for its answer
        assertThat(invoked).containsExactlyInAnyOrder("tracker", "tracker", "writer");
    }
}
