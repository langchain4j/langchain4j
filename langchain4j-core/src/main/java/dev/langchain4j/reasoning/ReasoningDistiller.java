package dev.langchain4j.reasoning;

import dev.langchain4j.Experimental;
import java.util.List;

/**
 * Distills raw {@link ReasoningTrace}s into generalizable {@link ReasoningStrategy} instances.
 * <p>
 * The distillation process transforms specific task executions into reusable strategies
 * that can be applied to similar but novel tasks. This is a core component of the
 * ReasoningBank framework that enables agent self-improvement through accumulated experience.
 * <p>
 * Different implementations can use various distillation approaches:
 * <ul>
 *   <li>Simple extraction from single successful traces</li>
 *   <li>LLM-based summarization and generalization</li>
 *   <li>Contrastive analysis of successful vs failed traces</li>
 *   <li>Memory-Aware Test-Time Scaling (MaTTS) for enhanced distillation</li>
 * </ul>
 *
 * @since 1.11.0
 */
@Experimental
public interface ReasoningDistiller {

    /**
     * Distills a single reasoning trace into zero or more strategies.
     * <p>
     * May return empty list if the trace doesn't contain useful reasoning
     * or if the distiller requires multiple traces for comparison.
     *
     * @param trace The reasoning trace to distill.
     * @return A list of distilled strategies (may be empty).
     */
    List<ReasoningStrategy> distill(ReasoningTrace trace);

    /**
     * Distills multiple reasoning traces into strategies.
     * <p>
     * Implementations may leverage contrastive learning by comparing
     * successful and failed traces to extract more robust strategies.
     *
     * @param traces The reasoning traces to distill.
     * @return A list of distilled strategies.
     */
    List<ReasoningStrategy> distillAll(List<ReasoningTrace> traces);

    /**
     * Refines an existing strategy based on new evidence from traces.
     * <p>
     * This enables iterative improvement of strategies as more experiences
     * are accumulated.
     *
     * @param existingStrategy The strategy to refine.
     * @param newTraces        New traces to incorporate.
     * @return The refined strategy.
     */
    default ReasoningStrategy refine(ReasoningStrategy existingStrategy, List<ReasoningTrace> newTraces) {
        // Default implementation returns the existing strategy unchanged
        return existingStrategy;
    }
}
