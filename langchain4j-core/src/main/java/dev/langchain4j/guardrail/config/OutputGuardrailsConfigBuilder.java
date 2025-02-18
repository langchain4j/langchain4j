package dev.langchain4j.guardrail.config;

/**
 * Builder for {@link OutputGuardrailsConfig} instances.
 * <p>
 *     This is needed so other frameworks (like Quarkus and Spring) can extend the configuration mechanism with their own
 *     implementations while also adhering to the interfaces and specs defined here.
 * </p>
 */
public interface OutputGuardrailsConfigBuilder extends GuardrailsConfigBuilder<OutputGuardrailsConfig> {
    /**
     * Sets the maximum number of retries for output guardrails.
     * <p>
     *     Defaults to {@link OutputGuardrailsConfig#maxRetries()} if not set.
     * </p>
     * @param maxRetries The maximum number of retries for output guardrails
     * @return The maximum number of retries for output guardrails
     * @see OutputGuardrailsConfig#maxRetries()
     */
    OutputGuardrailsConfigBuilder maxRetries(int maxRetries);
}
