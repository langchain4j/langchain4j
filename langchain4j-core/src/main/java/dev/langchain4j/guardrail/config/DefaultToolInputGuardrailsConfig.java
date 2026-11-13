package dev.langchain4j.guardrail.config;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

/**
 * The default implementation of {@link ToolInputGuardrailsConfig} for this library if no other libraries provide their own implementations.
 */
final class DefaultToolInputGuardrailsConfig implements ToolInputGuardrailsConfig {
    DefaultToolInputGuardrailsConfig(Builder builder) {
        ensureNotNull(builder, "builder");
    }

    /**
     * Gets a builder instance for building {@link DefaultToolInputGuardrailsConfig} instances.
     * @return The builder instance for building {@link DefaultToolInputGuardrailsConfig} instances.
     */
    static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link DefaultToolInputGuardrailsConfig} instances.
     */
    static class Builder implements ToolInputGuardrailsConfigBuilder {
        @Override
        public ToolInputGuardrailsConfig build() {
            return new DefaultToolInputGuardrailsConfig(this);
        }
    }
}
