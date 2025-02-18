package dev.langchain4j.guardrail.config;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

/**
 * The default implementation of {@link InputGuardrailsConfig} for this library if no other libraries provide their own implementations.
 */
final class DefaultInputGuardrailsConfig implements InputGuardrailsConfig {
    DefaultInputGuardrailsConfig(Builder builder) {
        ensureNotNull(builder, "builder");
    }

    /**
     * Gets a builder instance for building {@link DefaultInputGuardrailsConfig} instances.
     * @return The builder instance for building {@link DefaultInputGuardrailsConfig} instances.
     */
    static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link DefaultInputGuardrailsConfig} instances.
     */
    static class Builder implements InputGuardrailsConfigBuilder {
        @Override
        public InputGuardrailsConfig build() {
            return new DefaultInputGuardrailsConfig(this);
        }
    }
}
