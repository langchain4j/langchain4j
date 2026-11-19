package dev.langchain4j.guardrail.config;

import java.util.ServiceLoader;
import dev.langchain4j.Experimental;
import dev.langchain4j.spi.guardrail.config.ToolInputGuardrailsConfigBuilderFactory;

/**
 * Configuration specifically for tool input guardrails.
 * <p>
 *     The default configuration has no settings.
 *     Frameworks that extend this library (like Quarkus or Spring) may provide their own implementations of this configuration.
 * </p>
 *
 * @since 1.25.0
 */
@Experimental
public interface ToolInputGuardrailsConfig extends GuardrailsConfig {
    /**
     * Gets a builder instance for building {@link ToolInputGuardrailsConfig} instances.
     * @return A {@link ToolInputGuardrailsConfigBuilder} for building {@link ToolInputGuardrailsConfig} instances.
     */
    static ToolInputGuardrailsConfigBuilder builder() {
        return ServiceLoader.load(ToolInputGuardrailsConfigBuilderFactory.class)
                .findFirst()
                .map(ToolInputGuardrailsConfigBuilderFactory::get)
                .orElseGet(DefaultToolInputGuardrailsConfig::builder);
    }

    /**
     * Builder for {@link ToolInputGuardrailsConfig} instances.
     * <p>
     *     This is needed so other frameworks (like Quarkus and Spring) can extend the configuration mechanism with their own
     *     implementations while also adhering to the interfaces and specs defined here.
     * </p>
     *
     * @since 1.25.0
     */
    @Experimental
    interface ToolInputGuardrailsConfigBuilder extends GuardrailsConfigBuilder<ToolInputGuardrailsConfig> {}
}
