package dev.langchain4j.reasoning;

import dev.langchain4j.Experimental;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * A simple implementation of {@link ReasoningDistiller} that extracts strategies
 * directly from reasoning traces without LLM-based summarization.
 * <p>
 * This implementation:
 * <ul>
 *   <li>Creates strategies from successful traces by using the thinking as the strategy</li>
 *   <li>Groups similar tasks and extracts common patterns from failed traces as pitfalls</li>
 *   <li>Assigns confidence scores based on success rate</li>
 * </ul>
 *
 * @since 1.11.0
 */
@Experimental
public class SimpleReasoningDistiller implements ReasoningDistiller {

    private final double baseConfidence;
    private final boolean learnFromFailures;

    /**
     * Creates a new simple distiller with default settings.
     */
    public SimpleReasoningDistiller() {
        this(0.6, true);
    }

    /**
     * Creates a new simple distiller with custom settings.
     *
     * @param baseConfidence    Base confidence score for single-trace strategies (0.0-1.0).
     * @param learnFromFailures Whether to extract pitfalls from failed traces.
     */
    public SimpleReasoningDistiller(double baseConfidence, boolean learnFromFailures) {
        this.baseConfidence = Math.max(0.0, Math.min(1.0, baseConfidence));
        this.learnFromFailures = learnFromFailures;
    }

    @Override
    public List<ReasoningStrategy> distill(ReasoningTrace trace) {
        if (trace == null) {
            return List.of();
        }

        // Only create strategies from successful traces with thinking
        if (!trace.isSuccessful()
                || trace.thinking() == null
                || trace.thinking().isBlank()) {
            return List.of();
        }

        ReasoningStrategy strategy = ReasoningStrategy.builder()
                .taskPattern(trace.taskDescription())
                .strategy(trace.thinking())
                .confidenceScore(baseConfidence)
                .metadata(trace.metadata())
                .build();

        return List.of(strategy);
    }

    @Override
    public List<ReasoningStrategy> distillAll(List<ReasoningTrace> traces) {
        if (traces == null || traces.isEmpty()) {
            return List.of();
        }

        // Group traces by similar task descriptions (simplified grouping)
        Map<String, List<ReasoningTrace>> grouped =
                traces.stream().collect(Collectors.groupingBy(this::normalizeTaskDescription));

        List<ReasoningStrategy> strategies = new ArrayList<>();

        for (Map.Entry<String, List<ReasoningTrace>> entry : grouped.entrySet()) {
            List<ReasoningTrace> groupTraces = entry.getValue();

            List<ReasoningTrace> successfulTraces = groupTraces.stream()
                    .filter(ReasoningTrace::isSuccessful)
                    .filter(t -> t.thinking() != null && !t.thinking().isBlank())
                    .toList();

            List<ReasoningTrace> failedTraces = groupTraces.stream()
                    .filter(t -> !t.isSuccessful())
                    .filter(t -> t.thinking() != null && !t.thinking().isBlank())
                    .toList();

            if (successfulTraces.isEmpty()) {
                continue;
            }

            // Calculate confidence based on success rate
            double successRate = (double) successfulTraces.size() / groupTraces.size();
            double confidence = baseConfidence * (0.5 + 0.5 * successRate);

            // Use the thinking from the most recent successful trace
            ReasoningTrace bestTrace = successfulTraces.get(successfulTraces.size() - 1);

            // Extract pitfalls from failures if enabled
            String pitfalls = null;
            if (learnFromFailures && !failedTraces.isEmpty()) {
                pitfalls = extractPitfalls(failedTraces);
            }

            ReasoningStrategy strategy = ReasoningStrategy.builder()
                    .taskPattern(bestTrace.taskDescription())
                    .strategy(bestTrace.thinking())
                    .pitfallsToAvoid(pitfalls)
                    .confidenceScore(confidence)
                    .metadata(bestTrace.metadata())
                    .build();

            strategies.add(strategy);
        }

        return strategies;
    }

    @Override
    public ReasoningStrategy refine(ReasoningStrategy existingStrategy, List<ReasoningTrace> newTraces) {
        if (newTraces == null || newTraces.isEmpty()) {
            return existingStrategy;
        }

        // Count successes and failures in new traces
        long successes = newTraces.stream().filter(ReasoningTrace::isSuccessful).count();
        long total = newTraces.size();

        // Adjust confidence based on new evidence
        double newSuccessRate = (double) successes / total;
        double adjustedConfidence = existingStrategy.confidenceScore() * 0.7 + newSuccessRate * 0.3;

        // Extract new pitfalls from failures
        String additionalPitfalls = null;
        if (learnFromFailures) {
            List<ReasoningTrace> failures = newTraces.stream()
                    .filter(t -> !t.isSuccessful())
                    .filter(t -> t.thinking() != null)
                    .toList();
            if (!failures.isEmpty()) {
                additionalPitfalls = extractPitfalls(failures);
            }
        }

        // Combine existing and new pitfalls
        String combinedPitfalls = existingStrategy.pitfallsToAvoid();
        if (additionalPitfalls != null) {
            combinedPitfalls =
                    combinedPitfalls != null ? combinedPitfalls + "; " + additionalPitfalls : additionalPitfalls;
        }

        return ReasoningStrategy.builder()
                .taskPattern(existingStrategy.taskPattern())
                .strategy(existingStrategy.strategy())
                .pitfallsToAvoid(combinedPitfalls)
                .confidenceScore(adjustedConfidence)
                .metadata(existingStrategy.metadata())
                .build();
    }

    /**
     * Normalizes a task description for grouping similar tasks.
     */
    private String normalizeTaskDescription(ReasoningTrace trace) {
        String desc = trace.taskDescription().toLowerCase().trim();
        // Simple normalization - could be improved with better text processing
        return desc.replaceAll("\\s+", " ").replaceAll("[^a-z0-9 ]", "").substring(0, Math.min(50, desc.length()));
    }

    /**
     * Extracts pitfalls from failed traces.
     */
    private String extractPitfalls(List<ReasoningTrace> failedTraces) {
        // Simple extraction - take key phrases from failed thinking
        StringBuilder pitfalls = new StringBuilder();
        int count = 0;
        for (ReasoningTrace trace : failedTraces) {
            if (count >= 3) break; // Limit to 3 pitfalls
            String thinking = trace.thinking();
            if (thinking != null && thinking.length() > 20) {
                // Take first sentence or first 100 chars as a pitfall indicator
                String snippet = thinking.split("[.!?]")[0];
                if (snippet.length() > 100) {
                    snippet = snippet.substring(0, 100) + "...";
                }
                if (pitfalls.length() > 0) {
                    pitfalls.append("; ");
                }
                pitfalls.append("Avoid: ").append(snippet.trim());
                count++;
            }
        }
        return pitfalls.length() > 0 ? pitfalls.toString() : null;
    }

    /**
     * Creates a new builder.
     *
     * @return A new builder instance.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for SimpleReasoningDistiller.
     */
    public static class Builder {

        private double baseConfidence = 0.6;
        private boolean learnFromFailures = true;

        public Builder baseConfidence(double baseConfidence) {
            this.baseConfidence = baseConfidence;
            return this;
        }

        public Builder learnFromFailures(boolean learnFromFailures) {
            this.learnFromFailures = learnFromFailures;
            return this;
        }

        public SimpleReasoningDistiller build() {
            return new SimpleReasoningDistiller(baseConfidence, learnFromFailures);
        }
    }
}
