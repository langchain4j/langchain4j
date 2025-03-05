package dev.langchain4j.guardrail.config;

import dev.langchain4j.spi.guardrail.config.InputGuardrailsConfigBuilderFactory;
import java.util.ServiceLoader;

/**
 * Builder for {@link InputGuardrailsConfig} instances.
 * <p>
 *     This is needed so other frameworks (like Quarkus and Spring) can extend the configuration mechanism with their own
 *     implementations while also adhering to the interfaces and specs defined here.
 * </p>
 */
public interface InputGuardrailsConfigBuilder extends GuardrailsConfigBuilder<InputGuardrailsConfig> {
    /**
     * Gets a builder instance for building {@link InputGuardrailsConfig} instances.
     * @return A {@link InputGuardrailsConfigBuilder} for building {@link InputGuardrailsConfig} instances.
     */
    static InputGuardrailsConfigBuilder newBuilder() {
        return ServiceLoader.load(InputGuardrailsConfigBuilderFactory.class)
                .findFirst()
                .map(InputGuardrailsConfigBuilderFactory::get)
                .orElseGet(DefaultInputGuardrailsConfig::builder);
    }
}
