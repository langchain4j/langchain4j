package dev.langchain4j.guardrail.config;

import dev.langchain4j.spi.guardrail.config.InputGuardrailsConfigBuilderFactory;
import java.util.ServiceLoader;

/**
 * Configuration specifically for input guardrails.
 * <p>
 *     Frameworks that extend this library (like Quarkus or Spring) may provide their own implementations of this configuration.
 * </p>
 */
public interface InputGuardrailsConfig extends GuardrailsConfig {
    /**
     * Gets a builder instance for building {@link InputGuardrailsConfig} instances.
     * @return A {@link InputGuardrailsConfigBuilder} for building {@link InputGuardrailsConfig} instances.
     */
    static InputGuardrailsConfigBuilder builder() {
        return ServiceLoader.load(InputGuardrailsConfigBuilderFactory.class)
                .findFirst()
                .map(InputGuardrailsConfigBuilderFactory::get)
                .orElseGet(DefaultInputGuardrailsConfig::builder);
    }

    /**
     * Builder for {@link InputGuardrailsConfig} instances.
     * <p>
     *     This is needed so other frameworks (like Quarkus and Spring) can extend the configuration mechanism with their own
     *     implementations while also adhering to the interfaces and specs defined here.
     * </p>
     */
    interface InputGuardrailsConfigBuilder extends GuardrailsConfigBuilder<InputGuardrailsConfig> {}
}
