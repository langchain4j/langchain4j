package dev.langchain4j.reasoning;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class SimpleReasoningDistillerTest {

    private final SimpleReasoningDistiller distiller = new SimpleReasoningDistiller();

    @Test
    void should_distill_successful_trace() {
        ReasoningTrace trace =
                ReasoningTrace.successful("Solve x + 5 = 10", "Subtract 5 from both sides to get x = 5", "x = 5");

        List<ReasoningStrategy> strategies = distiller.distill(trace);

        assertThat(strategies).hasSize(1);
        assertThat(strategies.get(0).taskPattern()).isEqualTo("Solve x + 5 = 10");
        assertThat(strategies.get(0).strategy()).isEqualTo("Subtract 5 from both sides to get x = 5");
    }

    @Test
    void should_not_distill_failed_trace() {
        ReasoningTrace trace = ReasoningTrace.failed("Impossible task", "This didn't work");

        List<ReasoningStrategy> strategies = distiller.distill(trace);

        assertThat(strategies).isEmpty();
    }

    @Test
    void should_not_distill_trace_without_thinking() {
        ReasoningTrace trace = ReasoningTrace.builder()
                .taskDescription("Task")
                .solution("Solution")
                .successful(true)
                .build();

        List<ReasoningStrategy> strategies = distiller.distill(trace);

        assertThat(strategies).isEmpty();
    }

    @Test
    void should_return_empty_for_null_trace() {
        List<ReasoningStrategy> strategies = distiller.distill(null);

        assertThat(strategies).isEmpty();
    }

    @Test
    void should_distill_multiple_traces() {
        List<ReasoningTrace> traces = List.of(
                ReasoningTrace.successful("Math task", "Use algebra", "result1"),
                ReasoningTrace.successful("Code task", "Write tests", "result2"),
                ReasoningTrace.failed("Failed task", "This approach failed"));

        List<ReasoningStrategy> strategies = distiller.distillAll(traces);

        // Should have strategies from the 2 successful traces
        assertThat(strategies).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    void should_include_pitfalls_from_failures() {
        SimpleReasoningDistiller distillerWithFailures =
                SimpleReasoningDistiller.builder().learnFromFailures(true).build();

        // Create traces with same task pattern (will be grouped)
        List<ReasoningTrace> traces = List.of(
                ReasoningTrace.successful("math problem", "Correct approach works", "5"),
                ReasoningTrace.builder()
                        .taskDescription("math problem")
                        .thinking("Wrong approach that led to error")
                        .successful(false)
                        .build());

        List<ReasoningStrategy> strategies = distillerWithFailures.distillAll(traces);

        // The distiller groups by normalized task description, so at least one strategy should exist
        assertThat(strategies).isNotEmpty();
    }

    @Test
    void should_calculate_confidence_based_on_success_rate() {
        SimpleReasoningDistiller customDistiller =
                SimpleReasoningDistiller.builder().baseConfidence(0.8).build();

        ReasoningTrace trace = ReasoningTrace.successful("task", "thinking", "solution");
        List<ReasoningStrategy> strategies = customDistiller.distill(trace);

        assertThat(strategies).hasSize(1);
        assertThat(strategies.get(0).confidenceScore()).isEqualTo(0.8);
    }

    @Test
    void should_refine_strategy_with_new_traces() {
        ReasoningStrategy originalStrategy = ReasoningStrategy.builder()
                .taskPattern("API calls")
                .strategy("Always handle errors")
                .confidenceScore(0.5)
                .build();

        // New successful traces
        List<ReasoningTrace> newTraces = List.of(
                ReasoningTrace.successful("API calls", "Check response codes", "done"),
                ReasoningTrace.successful("API calls", "Use timeouts", "done"));

        ReasoningStrategy refined = distiller.refine(originalStrategy, newTraces);

        // Confidence should increase due to successful traces
        assertThat(refined.confidenceScore()).isGreaterThan(originalStrategy.confidenceScore());
        assertThat(refined.taskPattern()).isEqualTo(originalStrategy.taskPattern());
    }

    @Test
    void should_return_original_strategy_when_no_new_traces() {
        ReasoningStrategy original = ReasoningStrategy.from("task", "strategy");

        ReasoningStrategy refined = distiller.refine(original, null);

        assertThat(refined).isEqualTo(original);
    }

    @Test
    void should_return_empty_for_empty_trace_list() {
        List<ReasoningStrategy> strategies = distiller.distillAll(List.of());

        assertThat(strategies).isEmpty();
    }

    @Test
    void should_return_empty_for_null_trace_list() {
        List<ReasoningStrategy> strategies = distiller.distillAll(null);

        assertThat(strategies).isEmpty();
    }

    @Test
    void should_create_distiller_with_builder() {
        SimpleReasoningDistiller customDistiller = SimpleReasoningDistiller.builder()
                .baseConfidence(0.7)
                .learnFromFailures(false)
                .build();

        assertThat(customDistiller).isNotNull();
    }
}
