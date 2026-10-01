package dev.langchain4j.service.decision;

import dev.langchain4j.Experimental;

/**
 * Provides the threshold for {@code boolean} answers of a decision service: the answer is {@code true} when the
 * probability of "yes" is greater than or equal to the threshold.
 * <p>
 * It is called on every invocation, so thresholds can come from configuration that changes at runtime.
 * It can be called concurrently, and for asynchronous methods on the thread that completes the call to the model
 * (for example an I/O thread), so it must be thread-safe, fast and non-blocking.
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
