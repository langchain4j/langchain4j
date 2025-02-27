package dev.langchain4j.guardrail.config;

import dev.langchain4j.spi.guardrail.config.InputGuardrailsConfigBuilderFactory;
import dev.langchain4j.spi.guardrail.config.OutputGuardrailsConfigBuilderFactory;
import java.util.ServiceLoader;

/**
 * Factory class for returning {@link GuardrailsConfigBuilder} instances.
 * <p>
 *     This is needed so other frameworks (like Quarkus and Spring) can extend the configuration mechanism with their own
 *     implementations while also adhering to the interfaces and specs defined here.
 * </p>
 */
public final class GuardrailsConfigBuilderFactory {
    private GuardrailsConfigBuilderFactory() {}

    /**
     * Gets a builder instance for building {@link InputGuardrailsConfig} instances.
     * @return A {@link InputGuardrailsConfigBuilder} for building {@link InputGuardrailsConfig} instances.
     */
    public static InputGuardrailsConfigBuilder inputGuardrailsConfigBuilder() {
        for (InputGuardrailsConfigBuilderFactory factory :
                ServiceLoader.load(InputGuardrailsConfigBuilderFactory.class)) {
            return factory.get();
        }

        return DefaultInputGuardrailsConfig.builder();
    }

    /**
     * Gets a builder instance for building {@link OutputGuardrailsConfig} instances.
     * @return A {@link OutputGuardrailsConfigBuilder} for building {@link OutputGuardrailsConfig} instances.
     */
    public static OutputGuardrailsConfigBuilder outputGuardrailsConfigBuilder() {
        for (OutputGuardrailsConfigBuilderFactory factory :
                ServiceLoader.load(OutputGuardrailsConfigBuilderFactory.class)) {
            return factory.get();
        }

        return DefaultOutputGuardrailsConfig.builder();
    }
}
