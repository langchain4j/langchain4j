package dev.langchain4j.guardrail.config;

import dev.langchain4j.Experimental;
import dev.langchain4j.spi.guardrail.config.ToolInputGuardrailsConfigBuilderFactory;
import java.util.ServiceLoader;

/**
 * Configuration specifically for tool input guardrails.
 * <p>
 *     Currently there is nothing to configure: unlike {@link InputGuardrailsConfig}, tool guardrails have no retries.
 *     Frameworks that extend this library (like Quarkus or Spring) may provide their own implementations of this configuration.
 * </p>
 *
 * @since 1.24.0
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
     * @since 1.24.0
     */
    @Experimental
    interface ToolInputGuardrailsConfigBuilder extends GuardrailsConfigBuilder<ToolInputGuardrailsConfig> {}
}
