package dev.langchain4j.guardrail.config;

/**
 * Builder for {@link GuardrailsConfig} instances.
 * @param <C> The type of configuration being build
 */
public interface GuardrailsConfigBuilder<C extends GuardrailsConfig> {
    /**
     * Builds the configuration.
     * @return The configuration
     */
    C build();
}
