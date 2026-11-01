package dev.langchain4j.model.bedrock;

import java.util.Objects;

/**
 * @see <a href="https://docs.aws.amazon.com/bedrock/latest/userguide/guardrails.html">Amazon Bedrock Guardrails</a>
 */
public class BedrockGuardrailConfiguration {

    private final String guardrailIdentifier;
    private final String guardrailVersion;
    private final ProcessingMode streamProcessingMode;
    private final BedrockGuardContentPlacement guardContentPlacement;

    public BedrockGuardrailConfiguration(
            String guardrailIdentifier, String guardrailVersion, ProcessingMode streamProcessingMode) {
        this.guardrailIdentifier = guardrailIdentifier;
        this.guardrailVersion = guardrailVersion;
        this.streamProcessingMode = streamProcessingMode;
        this.guardContentPlacement = null;
    }

    private BedrockGuardrailConfiguration(Builder builder) {
        this.guardrailIdentifier = builder.guardrailIdentifier;
        this.guardrailVersion = builder.guardrailVersion;
        this.streamProcessingMode = builder.streamProcessingMode;
        this.guardContentPlacement = builder.guardContentPlacement;
    }

    public String guardrailIdentifier() {
        return guardrailIdentifier;
    }

    public String guardrailVersion() {
        return guardrailVersion;
    }

    public ProcessingMode streamProcessingMode() {
        return streamProcessingMode;
    }

    public BedrockGuardContentPlacement guardContentPlacement() {
        return guardContentPlacement;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BedrockGuardrailConfiguration that = (BedrockGuardrailConfiguration) o;
        return Objects.equals(guardrailIdentifier, that.guardrailIdentifier)
                && Objects.equals(guardrailVersion, that.guardrailVersion)
                && Objects.equals(streamProcessingMode, that.streamProcessingMode)
                && Objects.equals(guardContentPlacement, that.guardContentPlacement);
    }

    @Override
    public int hashCode() {
        return Objects.hash(guardrailIdentifier, guardrailVersion, streamProcessingMode, guardContentPlacement);
    }

    @Override
    public String toString() {
        return "BedrockGuardrailConfiguration{" + "guardrailIdentifier='" + guardrailIdentifier + '\''
                + ", guardrailVersion='" + guardrailVersion + '\'' + ", streamProcessingMode=" + streamProcessingMode
                + ", guardContentPlacement=" + guardContentPlacement
                + '}';
    }

    public enum ProcessingMode {
        SYNC,
        ASYNC,
    }

    public static class Builder {

        private String guardrailIdentifier;
        private String guardrailVersion;
        private ProcessingMode streamProcessingMode;
        private BedrockGuardContentPlacement guardContentPlacement;

        /**
         * Sets the identifier for the guardrail.
         * @param guardrailIdentifier The identifier for the guardrail.
         * @return this builder
         */
        public Builder guardrailIdentifier(String guardrailIdentifier) {
            this.guardrailIdentifier = guardrailIdentifier;
            return this;
        }

        /**
         * Sets the version of the guardrail.
         * @param guardrailVersion The version of the guardrail.
         * @return this builder
         */
        public Builder guardrailVersion(String guardrailVersion) {
            this.guardrailVersion = guardrailVersion;
            return this;
        }

        /**
         * Sets the processing mode for converse streaming.
         *
         * See <a href="https://docs.aws.amazon.com/bedrock/latest/userguide/guardrails-streaming.html">Configure streaming response behavior.</a>
         *
         * @param streamProcessingMode The processing mode.
         * @return this builder
         */
        public Builder streamProcessingMode(ProcessingMode streamProcessingMode) {
            this.streamProcessingMode = streamProcessingMode;
            return this;
        }

        /**
         * Sets which user messages the guardrail evaluates as input, by wrapping them in Bedrock Converse
         * {@code guardContent} blocks.
         * <p>
         * When not set, the guardrail evaluates all user and AI messages.
         * When set, the guardrail evaluates <b>only</b> the selected user messages as input:
         * other user messages and AI messages are not evaluated. The model response is still evaluated.
         * See {@link BedrockGuardContentPlacement} for details.
         *
         * @param guardContentPlacement the guard content placement;
         *                              {@code null} means all user and AI messages are evaluated
         * @return this builder
         */
        public Builder guardContentPlacement(BedrockGuardContentPlacement guardContentPlacement) {
            this.guardContentPlacement = guardContentPlacement;
            return this;
        }

        public BedrockGuardrailConfiguration build() {
            return new BedrockGuardrailConfiguration(this);
        }
    }
}
