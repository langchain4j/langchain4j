package dev.langchain4j.guardrail.config;

import dev.langchain4j.Experimental;
import dev.langchain4j.spi.guardrail.config.ToolOutputGuardrailsConfigBuilderFactory;
import java.util.ServiceLoader;

/**
 * Configuration specifically for tool output guardrails.
 * <p>
 *     Currently there is nothing to configure: unlike {@link OutputGuardrailsConfig}, tool guardrails have no retries.
 *     Frameworks that extend this library (like Quarkus or Spring) may provide their own implementations of this configuration.
 * </p>
 *
 * @since 1.24.0
 */
@Experimental
public interface ToolOutputGuardrailsConfig extends GuardrailsConfig {
    /**
     * Gets a builder instance for building {@link ToolOutputGuardrailsConfig} instances.
     * @return A {@link ToolOutputGuardrailsConfigBuilder} for building {@link ToolOutputGuardrailsConfig} instances.
     */
    static ToolOutputGuardrailsConfigBuilder builder() {
        return ServiceLoader.load(ToolOutputGuardrailsConfigBuilderFactory.class)
                .findFirst()
                .map(ToolOutputGuardrailsConfigBuilderFactory::get)
                .orElseGet(DefaultToolOutputGuardrailsConfig::builder);
    }

    /**
     * Builder for {@link ToolOutputGuardrailsConfig} instances.
     * <p>
     *     This is needed so other frameworks (like Quarkus and Spring) can extend the configuration mechanism with their own
     *     implementations while also adhering to the interfaces and specs defined here.
     * </p>
     *
     * @since 1.24.0
     */
    @Experimental
    interface ToolOutputGuardrailsConfigBuilder extends GuardrailsConfigBuilder<ToolOutputGuardrailsConfig> {}
}
