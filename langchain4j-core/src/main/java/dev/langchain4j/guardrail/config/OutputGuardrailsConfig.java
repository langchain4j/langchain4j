package dev.langchain4j.guardrail.config;

/**
 * Configuration specifically for output guardrails.
 * <p>
 *     Frameworks that extend this library (like Quarkus or Spring) may provide their own implementations of this configuration.
 * </p>
 */
public interface OutputGuardrailsConfig extends GuardrailsConfig {
    /**
     * Default maximum number of retries for the guardrail.
     */
    int MAX_RETRIES_DEFAULT = 3;

    /**
     * Configures the maximum number of retries for the guardrail.
     * <p>
     *     Defaults to {@link #MAX_RETRIES_DEFAULT} if not set.
     * </p>
     * Set to {@code 0} to disable retries.
     */
    int maxRetries();
}
