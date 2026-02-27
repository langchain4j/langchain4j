package dev.langchain4j.experimental.durable.replay;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agentic.planner.Action;
import dev.langchain4j.agentic.planner.AgenticSystemTopology;
import dev.langchain4j.agentic.planner.InitPlanningContext;
import dev.langchain4j.agentic.planner.Planner;
import dev.langchain4j.agentic.planner.PlanningContext;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.experimental.durable.store.event.AgentInvocationCompletedEvent;
import dev.langchain4j.experimental.durable.store.event.TaskEvent;
import dev.langchain4j.experimental.durable.store.event.TaskResumedEvent;
import dev.langchain4j.experimental.durable.store.event.TaskStartedEvent;
import dev.langchain4j.experimental.durable.task.TaskId;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class ReplayingPlannerTest {

    @Test
    void should_delegate_directly_when_no_journal_events() {
        Planner delegate = new TestSequentialPlanner(3);
        AgenticScope scope = Mockito.mock(AgenticScope.class);

        ReplayingPlanner planner = ReplayingPlanner.builder()
                .delegate(delegate)
                .journalEvents(List.of())
                .build();

        planner.init(new InitPlanningContext(scope, null, List.of()));
        assertThat(planner.isReplayComplete()).isTrue();
        assertThat(planner.replayedCount()).isEqualTo(0);
    }

    @Test
    void should_replay_completed_invocations() {
        TaskId taskId = new TaskId("t1");
        Instant now = Instant.now();

        List<TaskEvent> events = List.of(
                new TaskStartedEvent(taskId, now, Map.of()),
                new AgentInvocationCompletedEvent(taskId, now, "agent1", "a1", "\"result1\""),
                new AgentInvocationCompletedEvent(taskId, now, "agent2", "a2", "\"result2\""));

        TestSequentialPlanner delegate = new TestSequentialPlanner(3);
        AgenticScope scope = Mockito.mock(AgenticScope.class);

        ReplayingPlanner planner = ReplayingPlanner.builder()
                .delegate(delegate)
                .journalEvents(events)
                .build();

        planner.init(new InitPlanningContext(scope, null, List.of()));

        // firstAction should replay 2 completed invocations and return the 3rd (live) action
        Action action = planner.firstAction(new PlanningContext(scope, null));

        assertThat(planner.isReplayComplete()).isTrue();
        assertThat(planner.replayedCount()).isEqualTo(2);
        // The delegate has been advanced past 2 steps, so the next action should be for step 3
        assertThat(action.isDone()).isFalse();
    }

    @Test
    void should_return_done_when_all_steps_were_completed() {
        TaskId taskId = new TaskId("t1");
        Instant now = Instant.now();

        List<TaskEvent> events = List.of(
                new TaskStartedEvent(taskId, now, Map.of()),
                new AgentInvocationCompletedEvent(taskId, now, "agent1", "a1", "\"r1\""),
                new AgentInvocationCompletedEvent(taskId, now, "agent2", "a2", "\"r2\""));

        // Delegate only has 2 steps — replay covers all
        TestSequentialPlanner delegate = new TestSequentialPlanner(2);
        AgenticScope scope = Mockito.mock(AgenticScope.class);

        ReplayingPlanner planner = ReplayingPlanner.builder()
                .delegate(delegate)
                .journalEvents(events)
                .build();

        planner.init(new InitPlanningContext(scope, null, List.of()));
        Action action = planner.firstAction(new PlanningContext(scope, null));

        assertThat(action.isDone()).isTrue();
        assertThat(planner.replayedCount()).isEqualTo(2);
    }

    @Test
    void should_report_topology_from_delegate() {
        Planner delegate = new TestSequentialPlanner(1);
        ReplayingPlanner planner = ReplayingPlanner.builder()
                .delegate(delegate)
                .journalEvents(List.of())
                .build();

        assertThat(planner.topology()).isEqualTo(AgenticSystemTopology.SEQUENCE);
    }

    @Test
    void should_write_user_input_from_resumed_events_to_scope() {
        TaskId taskId = new TaskId("t1");
        Instant now = Instant.now();

        List<TaskEvent> events = List.of(
                new TaskStartedEvent(taskId, now, Map.of()),
                new TaskResumedEvent(taskId, now, Map.of("approvalKey", "approved")),
                new TaskResumedEvent(taskId, now, Map.of("otherKey", 42))
        );

        TestSequentialPlanner delegate = new TestSequentialPlanner(1);
        AgenticScope scope = Mockito.mock(AgenticScope.class);

        ReplayingPlanner planner = ReplayingPlanner.builder()
                .delegate(delegate)
                .journalEvents(events)
                .build();

        planner.init(new InitPlanningContext(scope, null, List.of()));
        planner.firstAction(new PlanningContext(scope, null));

        Mockito.verify(scope).writeState("approvalKey", "approved");
        Mockito.verify(scope).writeState("otherKey", 42);
    }

    /**
     * A simple test planner that simulates a sequential execution of N steps.
     */
    private static class TestSequentialPlanner implements Planner {

        private final int totalSteps;
        private int cursor = 0;

        TestSequentialPlanner(int totalSteps) {
            this.totalSteps = totalSteps;
        }

        @Override
        public void init(InitPlanningContext initPlanningContext) {
            // no-op for test
        }

        @Override
        public Action firstAction(PlanningContext planningContext) {
            return nextAction(planningContext);
        }

        @Override
        public Action nextAction(PlanningContext planningContext) {
            if (cursor >= totalSteps) {
                return done();
            }
            cursor++;
            // Return a simple AgentCallAction with an empty agent list
            // (we won't actually execute agents in these tests)
            return new Action.AgentCallAction(List.of());
        }

        @Override
        public AgenticSystemTopology topology() {
            return AgenticSystemTopology.SEQUENCE;
        }

        @Override
        public boolean terminated() {
            return cursor >= totalSteps;
        }
    }
}
