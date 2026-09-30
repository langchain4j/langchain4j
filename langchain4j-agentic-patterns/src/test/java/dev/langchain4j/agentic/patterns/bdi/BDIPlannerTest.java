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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
        AtomicInteger orderCounter = new AtomicInteger();
        AtomicInteger lowOrder = new AtomicInteger();
        AtomicInteger highOrder = new AtomicInteger();

        var trackingA = new Object() {
            @Agent(outputKey = "outputA")
            public String producer() {
                lowOrder.set(orderCounter.incrementAndGet());
                return "resultA";
            }
        };
        var trackingC = new Object() {
            @Agent(outputKey = "outputC")
            public String producer(@V("trigger") String t) {
                highOrder.set(orderCounter.incrementAndGet());
                return "resultC";
            }
        };

        // Both desires achievable and unsatisfied from the start; only priority differs
        var lowPriority = Desire.of("low", 1,
                scope -> true, scope -> scope.hasState("outputA"), trackingA.getClass());
        var highPriority = Desire.of("high", 3,
                scope -> true, scope -> scope.hasState("outputC"), trackingC.getClass());

        UntypedAgent system = AgenticServices.plannerBuilder()
                .subAgents(trackingA, trackingC)
                .planner(() -> new BDIPlanner(List.of(lowPriority, highPriority)))
                .build();

        system.invokeWithAgenticScope(Map.of("trigger", "t"));
        assertThat(highOrder.get()).as("high-priority desire should run before low-priority").isLessThan(lowOrder.get());
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
        // "high" becomes achievable only after A writes outputA, so "low" is selected first
        var lowPriority = Desire.of("low", 1,
                scope -> true, scope -> scope.hasState("outputB"), ProducerA.class, ProducerB.class);
        var highPriority = Desire.of("high", 3,
                scope -> scope.hasState("outputA"), scope -> scope.hasState("outputC"), ProducerC.class);

        UntypedAgent system = AgenticServices.plannerBuilder()
                .subAgents(new ProducerA(), new ProducerB(), new ProducerC())
                .planner(() -> new BDIPlanner(List.of(lowPriority, highPriority)))
                .build();

        // trigger must be present for ProducerC's @V("trigger") parameter
        // sequence: A (low) -> preemption -> C (high) -> resume B (low)
        ResultWithAgenticScope<String> result = system.invokeWithAgenticScope(Map.of("trigger", "alert"));
        assertThat(result.agenticScope().hasState("outputA")).isTrue();
        assertThat(result.agenticScope().hasState("outputB")).isTrue();
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
    void shouldThrowWhenMaxInvocationsExceeded() {
        AtomicInteger totalCalls = new AtomicInteger();

        var countingA = new Object() {
            @Agent(outputKey = "outputA")
            public String producerA() {
                totalCalls.incrementAndGet();
                return "resultA";
            }
        };
        var countingB = new Object() {
            @Agent(outputKey = "outputB")
            public String producerB(@V("outputA") String input) {
                totalCalls.incrementAndGet();
                return "resultB";
            }
        };
        var countingC = new Object() {
            @Agent(outputKey = "outputC")
            public String producerC(@V("outputB") String input) {
                totalCalls.incrementAndGet();
                return "resultC";
            }
        };

        var desire = Desire.of("goal", 1,
                scope -> true, scope -> scope.hasState("outputC"),
                countingA.getClass(), countingB.getClass(), countingC.getClass());

        int max = 2;
        UntypedAgent system = AgenticServices.plannerBuilder()
                .subAgents(countingA, countingB, countingC)
                .planner(() -> new BDIPlanner(List.of(desire), max))
                .build();

        assertThatThrownBy(() -> system.invokeWithAgenticScope(Map.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Maximum invocations (2) reached");
        assertThat(totalCalls.get()).isEqualTo(max);
    }

    @Test
    void shouldThrowWhenIntentionExhaustedButDesireUnsatisfied() {
        // Desire expects "missing" in scope, but ProducerA writes "outputA" — intention
        // completes without satisfying the desire
        var desire = Desire.of("broken", 1,
                scope -> true, scope -> scope.hasState("missing"), ProducerA.class);

        UntypedAgent system = AgenticServices.plannerBuilder()
                .subAgents(new ProducerA())
                .planner(() -> new BDIPlanner(List.of(desire)))
                .build();

        assertThatThrownBy(() -> system.invokeWithAgenticScope(Map.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Desire 'broken' is still unsatisfied");
    }

    @Test
    void shouldThrowWhenPreemptedDesireResumesPastEnd() {
        // low = [A, B] never satisfies; high = [C] becomes achievable after B writes outputB.
        // After B completes, preemption saves cursor = 2 (past end of [A,B]).
        // When low is re-selected, the saved cursor hits the exhaustion check.
        var low = Desire.of("low", 1,
                scope -> true, scope -> scope.hasState("never"), ProducerA.class, ProducerB.class);
        var high = Desire.of("high", 3,
                "outputB", "outputC", ProducerC.class);

        UntypedAgent system = AgenticServices.plannerBuilder()
                .subAgents(new ProducerA(), new ProducerB(), new ProducerC())
                .planner(() -> new BDIPlanner(List.of(low, high)))
                .build();

        assertThatThrownBy(() -> system.invokeWithAgenticScope(Map.of("trigger", "t")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Desire 'low' is still unsatisfied");
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
