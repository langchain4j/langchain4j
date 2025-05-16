package dev.langchain4j.guardrail.config;

import dev.langchain4j.spi.guardrail.config.OutputGuardrailsConfigBuilderFactory;
import java.util.ServiceLoader;

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
    int MAX_RETRIES_DEFAULT = 2;

    /**
     * Configures the maximum number of retries for the guardrail.
     * <p>
     *     Defaults to {@link #MAX_RETRIES_DEFAULT} if not set.
     * </p>
     * Set to {@code 0} to disable retries.
     */
    int maxRetries();

    /**
     * Gets a newBuilder instance for building {@link OutputGuardrailsConfig} instances.
     * @return A {@link OutputGuardrailsConfigBuilder} for building {@link OutputGuardrailsConfig} instances.
     */
    static OutputGuardrailsConfigBuilder builder() {
        return ServiceLoader.load(OutputGuardrailsConfigBuilderFactory.class)
                .findFirst()
                .map(OutputGuardrailsConfigBuilderFactory::get)
                .orElseGet(DefaultOutputGuardrailsConfig::builder);
    }

    /**
     * Builder for {@link OutputGuardrailsConfig} instances.
     * <p>
     *     This is needed so other frameworks (like Quarkus and Spring) can extend the configuration mechanism with their own
     *     implementations while also adhering to the interfaces and specs defined here.
     * </p>
     */
    interface OutputGuardrailsConfigBuilder extends GuardrailsConfigBuilder<OutputGuardrailsConfig> {
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
}
