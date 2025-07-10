package dev.langchain4j.guardrail.config;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

/**
 * The default implementation of {@link OutputGuardrailsConfig} for this library if no other libraries provide their own implementations.
 */
final class DefaultOutputGuardrailsConfig implements OutputGuardrailsConfig {
    private final int maxRetries;

    DefaultOutputGuardrailsConfig(Builder builder) {
        ensureNotNull(builder, "builder");
        this.maxRetries = builder.maxRetries;
    }

    /**
     * Gets a builder instance for building {@link DefaultOutputGuardrailsConfig} instances.
     * @return The builder instance for building {@link DefaultOutputGuardrailsConfig} instances.
     */
    static Builder builder() {
        return new Builder();
    }

    @Override
    public int maxRetries() {
        return this.maxRetries;
    }

    /**
     * Builder for {@link DefaultOutputGuardrailsConfig} instances.
     */
    static class Builder implements OutputGuardrailsConfigBuilder {
        private int maxRetries = MAX_RETRIES_DEFAULT;

        @Override
        public Builder maxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        @Override
        public OutputGuardrailsConfig build() {
            return new DefaultOutputGuardrailsConfig(this);
        }
    }
}
