package dev.langchain4j.model.bedrock;

import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import java.util.Objects;

public class BedrockChatResponseMetadata extends ChatResponseMetadata {

    private final GuardrailAssessmentSummary guardrailAssessmentSummary;

    protected BedrockChatResponseMetadata(final Builder builder) {
        super(builder);
        this.guardrailAssessmentSummary = builder.guardrailAssessmentSummary;
    }

    public GuardrailAssessmentSummary guardrailAssessmentSummary() {
        return guardrailAssessmentSummary;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(final Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        BedrockChatResponseMetadata that = (BedrockChatResponseMetadata) o;
        return Objects.equals(guardrailAssessmentSummary, that.guardrailAssessmentSummary);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), guardrailAssessmentSummary);
    }

    @Override
    public String toString() {
        return "BedrockChatResponseMetadata{" + "guardrailAssessmentSummary=" + guardrailAssessmentSummary + "} "
                + super.toString();
    }

    public static class Builder extends ChatResponseMetadata.Builder<Builder> {

        private GuardrailAssessmentSummary guardrailAssessmentSummary;

        public Builder guardrailAssessmentSummary(GuardrailAssessmentSummary guardrailAssessmentSummary) {
            this.guardrailAssessmentSummary = guardrailAssessmentSummary;
            return this;
        }

        @Override
        public BedrockChatResponseMetadata build() {
            return new BedrockChatResponseMetadata(this);
        }
    }
}
