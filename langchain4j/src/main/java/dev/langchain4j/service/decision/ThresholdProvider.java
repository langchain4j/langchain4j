package dev.langchain4j.service.decision;

import dev.langchain4j.Experimental;

/**
 * Provides the threshold for {@code boolean} answers of a decision service: the answer is {@code true} when the
 * probability of "yes" is greater than or equal to the threshold.
 * <p>
 * It is called on every invocation, so thresholds can come from configuration that changes at runtime.
 * <pre>{@code
 * ThresholdProvider thresholds = context -> config.getDouble(
 *         context.serviceInterface().getSimpleName() + "." + context.questionName());
 * }</pre>
 *
 * @since 1.21.0
 */
@Experimental
@FunctionalInterface
public interface ThresholdProvider {

    /**
     * Returns the threshold for the given question, from 0 to 1, or {@code null} to use the default of 0.5.
     */
    Double threshold(ThresholdContext context);
}
