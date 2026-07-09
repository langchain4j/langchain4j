package dev.langchain4j.agentic.patterns.bdi;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.scope.ResultWithAgenticScope;
import dev.langchain4j.service.V;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class BDIPlannerTest {

    // ── Non-AI agents that write deterministic values to scope ──

    public static class ProducerA {
        @Agent(outputKey = "outputA")
        public String producerA() {
            return "resultA";
        }
    }

    public static class ProducerB {
        @Agent(outputKey = "outputB")
        public String producerB(@V("outputA") String input) {
            return "resultB from " + input;
        }
    }

    public static class ProducerC {
        @Agent(outputKey = "outputC")
        public String producerC(@V("trigger") String trigger) {
            return "resultC from " + trigger;
        }
    }

    @Test
    void shouldSelectHighestPriorityAchievableDesire() {
        var lowPriority = Desire.of("low", 1,
                scope -> true, scope -> scope.hasState("outputA"), ProducerA.class);
        var highPriority = Desire.of("high", 3,
                scope -> true, scope -> scope.hasState("outputB"), ProducerB.class);

        UntypedAgent system = AgenticServices.plannerBuilder()
                .subAgents(new ProducerA(), new ProducerB())
                .planner(() -> new BDIPlanner(List.of(lowPriority, highPriority)))
                .build();

        ResultWithAgenticScope<String> result = system.invokeWithAgenticScope(Map.of("outputA", "seed"));
        assertThat(result.agenticScope().hasState("outputB")).isTrue();
    }

    @Test
    void shouldProgressThroughIntentionSequence() {
        var desire = Desire.of("goal", 1,
                scope -> true, scope -> scope.hasState("outputB"), ProducerA.class, ProducerB.class);

        UntypedAgent system = AgenticServices.plannerBuilder()
                .subAgents(new ProducerA(), new ProducerB())
                .planner(() -> new BDIPlanner(List.of(desire)))
                .build();

        ResultWithAgenticScope<String> result = system.invokeWithAgenticScope(Map.of());
        assertThat(result.agenticScope().readState("outputA", "")).isEqualTo("resultA");
        assertThat(result.agenticScope().readState("outputB", "")).isEqualTo("resultB from resultA");
    }

    @Test
    void shouldPreemptLowerPriorityDesire() {
        var lowPriority = Desire.of("low", 1,
                scope -> true, scope -> scope.hasState("outputB"), ProducerA.class, ProducerB.class);
        var highPriority = Desire.of("high", 3,
                scope -> scope.hasState("trigger"), scope -> scope.hasState("outputC"), ProducerC.class);

        UntypedAgent system = AgenticServices.plannerBuilder()
                .subAgents(new ProducerA(), new ProducerB(), new ProducerC())
                .planner(() -> new BDIPlanner(List.of(lowPriority, highPriority)))
                .build();

        // trigger is present from the start -> high-priority desire preempts after first agent
        ResultWithAgenticScope<String> result = system.invokeWithAgenticScope(Map.of("trigger", "alert"));
        assertThat(result.agenticScope().hasState("outputC")).isTrue();
    }

    @Test
    void shouldResumePreemptedDesireWithoutRerunningCompletedAgents() {
        AtomicInteger producerACount = new AtomicInteger();

        var counting = new Object() {
            @Agent(outputKey = "outputA")
            public String producerA() {
                producerACount.incrementAndGet();
                return "resultA";
            }
        };

        // low = [countingA, B], high = [C] achievable only after outputA is written
        var lowPriority = Desire.of("low", 1,
                scope -> true, scope -> scope.hasState("outputB"), counting.getClass(), ProducerB.class);
        var highPriority = Desire.of("high", 3,
                scope -> scope.hasState("outputA"), scope -> scope.hasState("outputC"), ProducerC.class);

        UntypedAgent system = AgenticServices.plannerBuilder()
                .subAgents(counting, new ProducerB(), new ProducerC())
                .planner(() -> new BDIPlanner(List.of(lowPriority, highPriority)))
                .build();

        ResultWithAgenticScope<String> result = system.invokeWithAgenticScope(Map.of("trigger", "alert"));

        assertThat(result.agenticScope().hasState("outputA")).isTrue();
        assertThat(result.agenticScope().hasState("outputB")).isTrue();
        assertThat(result.agenticScope().hasState("outputC")).isTrue();
        // A should run exactly once: dispatched by "low", then "low" preempted by "high",
        // then "low" resumed at B — not restarted at A
        assertThat(producerACount.get()).isEqualTo(1);
    }

    @Test
    void shouldTerminateWhenAllDesiresSatisfied() {
        var desire = Desire.of("goal", 1,
                scope -> true, scope -> scope.hasState("outputA"), ProducerA.class);

        UntypedAgent system = AgenticServices.plannerBuilder()
                .subAgents(new ProducerA())
                .planner(() -> new BDIPlanner(List.of(desire)))
                .build();

        // outputA already present -> desire already satisfied -> done immediately
        ResultWithAgenticScope<String> result = system.invokeWithAgenticScope(Map.of("outputA", "already done"));
        assertThat(result.agenticScope().readState("outputA", "")).isEqualTo("already done");
    }

    @Test
    void shouldReDeliberateAfterDesireSatisfied() {
        var desire1 = Desire.of("first", 2,
                scope -> true, scope -> scope.hasState("outputA"), ProducerA.class);
        var desire2 = Desire.of("second", 1,
                scope -> scope.hasState("outputA"), scope -> scope.hasState("outputB"), ProducerB.class);

        UntypedAgent system = AgenticServices.plannerBuilder()
                .subAgents(new ProducerA(), new ProducerB())
                .planner(() -> new BDIPlanner(List.of(desire1, desire2)))
                .build();

        ResultWithAgenticScope<String> result = system.invokeWithAgenticScope(Map.of());
        assertThat(result.agenticScope().hasState("outputA")).isTrue();
        assertThat(result.agenticScope().hasState("outputB")).isTrue();
    }
}
