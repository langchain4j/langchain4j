package dev.langchain4j.guardrail.config;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.internal.JacocoIgnoreCoverageGenerated;
import java.util.Objects;

/**
 * The default implementation of {@link ToolInputGuardrailsConfig} for this library if no other libraries provide their own implementations.
 */
final class DefaultToolInputGuardrailsConfig implements ToolInputGuardrailsConfig {

    private final boolean isArgumentsRewriteNoteEnabled;
    private final String argumentsRewriteNoteTemplate;

    DefaultToolInputGuardrailsConfig(Builder builder) {
        ensureNotNull(builder, "builder");
        this.isArgumentsRewriteNoteEnabled = builder.isArgumentsRewriteNoteEnabled;
        this.argumentsRewriteNoteTemplate =
                getOrDefault(builder.argumentsRewriteNoteTemplate, DEFAULT_ARGUMENTS_REWRITE_NOTE_TEMPLATE);
    }

    @Override
    public boolean isArgumentsRewriteNoteEnabled() {
        return isArgumentsRewriteNoteEnabled;
    }

    @Override
    public String argumentsRewriteNoteTemplate() {
        return argumentsRewriteNoteTemplate;
    }

    @Override
    public boolean equals(Object o) {
        return (this == o)
                || ((o instanceof DefaultToolInputGuardrailsConfig that)
                        && (isArgumentsRewriteNoteEnabled == that.isArgumentsRewriteNoteEnabled)
                        && argumentsRewriteNoteTemplate.equals(that.argumentsRewriteNoteTemplate));
    }

    @Override
    public int hashCode() {
        return Objects.hash(isArgumentsRewriteNoteEnabled, argumentsRewriteNoteTemplate);
    }

    @Override
    @JacocoIgnoreCoverageGenerated
    public String toString() {
        return "DefaultToolInputGuardrailsConfig{isArgumentsRewriteNoteEnabled=%s, argumentsRewriteNoteTemplate='%s'}"
                .formatted(isArgumentsRewriteNoteEnabled, argumentsRewriteNoteTemplate);
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

        private boolean isArgumentsRewriteNoteEnabled = true;
        private String argumentsRewriteNoteTemplate;

        @Override
        public Builder argumentsRewriteNoteEnabled(boolean isEnabled) {
            this.isArgumentsRewriteNoteEnabled = isEnabled;
            return this;
        }

        @Override
        public Builder argumentsRewriteNoteTemplate(String template) {
            ensureNotNull(template, "template");
            if (!template.contains(RESULT_PLACEHOLDER)) {
                throw new IllegalArgumentException(
                        "The template of the arguments rewrite note must contain %s, or the LLM would not get the result of the tool"
                                .formatted(RESULT_PLACEHOLDER));
            }
            this.argumentsRewriteNoteTemplate = template;
            return this;
        }

        @Override
        public ToolInputGuardrailsConfig build() {
            return new DefaultToolInputGuardrailsConfig(this);
        }
    }
}
