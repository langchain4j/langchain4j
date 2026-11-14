package dev.langchain4j.guardrail.config;

import dev.langchain4j.internal.JacocoIgnoreCoverageGenerated;
import dev.langchain4j.spi.guardrail.config.OutputGuardrailsConfigBuilderFactory;
import java.util.ServiceLoader;

/**
 * Configuration specifically for output guardrails.
 * <p>
 *     Frameworks that extend this library (like Quarkus or Spring) may provide their own implementations of this configuration.
 * </p>
 */
@JacocoIgnoreCoverageGenerated
public interface OutputGuardrailsConfig extends GuardrailsConfig {
    /**
     * Default value of {@link #maxRetries()}: the first response of the model plus one retry or reprompt.
     */
    int MAX_RETRIES_DEFAULT = 2;

    /**
     * Returns the maximum number of times the output guardrails validate a response of the model, including the first
     * one: at most {@code maxRetries - 1} retries or reprompts. Unlike {@code maxRetries} on chat models, which counts
     * only the retries after the first call, this value includes the first response.
     * <p>
     *     The default implementation uses {@link #MAX_RETRIES_DEFAULT} (one retry or reprompt) if not set.
     * </p>
     * Set to {@code 0} or {@code 1} to disable retries. A negative value uses {@link #MAX_RETRIES_DEFAULT}.
     *
     * @return the maximum number of validations, including the first one
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
         * Sets the maximum number of times the output guardrails validate a response of the model, including the
         * first one.
         * <p>
         *     The default implementation uses {@link OutputGuardrailsConfig#MAX_RETRIES_DEFAULT} if not set.
         * </p>
         * @param maxRetries The maximum number of times a response is validated, including the first one
         * @return This builder
         * @see OutputGuardrailsConfig#maxRetries()
         */
        OutputGuardrailsConfigBuilder maxRetries(int maxRetries);
    }
}
