package dev.langchain4j.model.bedrock;

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
        this(guardrailIdentifier, guardrailVersion, streamProcessingMode, null);
    }

    public BedrockGuardrailConfiguration(
            String guardrailIdentifier,
            String guardrailVersion,
            ProcessingMode streamProcessingMode,
            BedrockGuardContentPlacement guardContentPlacement) {
        this.guardrailIdentifier = guardrailIdentifier;
        this.guardrailVersion = guardrailVersion;
        this.streamProcessingMode = streamProcessingMode;
        this.guardContentPlacement = guardContentPlacement;
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
         * Sets which user messages should be wrapped in Bedrock Converse {@code guardContent} blocks.
         *
         * @param guardContentPlacement the guard content placement strategy; null disables guardContent wrapping
         * @return this builder
         */
        public Builder guardContentPlacement(BedrockGuardContentPlacement guardContentPlacement) {
            this.guardContentPlacement = guardContentPlacement;
            return this;
        }

        public BedrockGuardrailConfiguration build() {
            return new BedrockGuardrailConfiguration(
                    guardrailIdentifier, guardrailVersion, streamProcessingMode, guardContentPlacement);
        }
    }
}
