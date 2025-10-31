package dev.langchain4j.model.bedrock;

/**
 * @see <a href="https://docs.aws.amazon.com/bedrock/latest/userguide/guardrails.html">Amazon Bedrock Guardrails</a>
 */
public class BedrockGuardrailConfiguration {
    private final String guardrailIdentifier;
    private final String guardrailVersion;

    public BedrockGuardrailConfiguration(final String guardrailIdentifier, final String guardrailVersion) {
        this.guardrailIdentifier = guardrailIdentifier;
        this.guardrailVersion = guardrailVersion;
    }

    public String getGuardrailIdentifier() {
        return guardrailIdentifier;
    }

    public String getGuardrailVersion() {
        return guardrailVersion;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String guardrailIdentifier;
        private String guardrailVersion;

        public Builder guardrailIdentifier(String guardrailIdentifier) {
            this.guardrailIdentifier = guardrailIdentifier;
            return this;
        }

        public Builder guardrailVersion(String guardrailVersion) {
            this.guardrailVersion = guardrailVersion;
            return this;
        }

        public BedrockGuardrailConfiguration build() {
            return new BedrockGuardrailConfiguration(guardrailIdentifier, guardrailVersion);
        }
    }
}
