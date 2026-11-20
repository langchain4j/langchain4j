package dev.langchain4j.guardrail.config;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

/**
 * The default implementation of {@link ToolOutputGuardrailsConfig} for this library if no other libraries provide their own implementations.
 */
final class DefaultToolOutputGuardrailsConfig implements ToolOutputGuardrailsConfig {
    DefaultToolOutputGuardrailsConfig(Builder builder) {
        ensureNotNull(builder, "builder");
    }

    /**
     * Gets a builder instance for building {@link DefaultToolOutputGuardrailsConfig} instances.
     * @return The builder instance for building {@link DefaultToolOutputGuardrailsConfig} instances.
     */
    static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link DefaultToolOutputGuardrailsConfig} instances.
     */
    static class Builder implements ToolOutputGuardrailsConfigBuilder {
        @Override
        public ToolOutputGuardrailsConfig build() {
            return new DefaultToolOutputGuardrailsConfig(this);
        }
    }
}
