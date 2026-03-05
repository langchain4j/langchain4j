package dev.langchain4j.model.bedrock;

import java.util.List;
import java.util.Objects;

public class GuardrailAssessmentSummary {
    private final List<GuardrailAssessment> inputAssessments;
    private final List<GuardrailAssessment> ouputAssessments;

    public GuardrailAssessmentSummary(Builder builder) {
        this.inputAssessments = builder.inputAssessments;
        this.ouputAssessments = builder.ouputAssessments;
    }

    public List<GuardrailAssessment> inputAssessments() {
        return inputAssessments;
    }

    public List<GuardrailAssessment> ouputAssessments() {
        return ouputAssessments;
    }

    public boolean hasAssessments() {
        return (inputAssessments != null && !inputAssessments.isEmpty())
                || (ouputAssessments != null && !ouputAssessments.isEmpty());
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(final Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        GuardrailAssessmentSummary that = (GuardrailAssessmentSummary) o;
        return Objects.equals(inputAssessments, that.inputAssessments)
                && Objects.equals(ouputAssessments, that.ouputAssessments);
    }

    @Override
    public int hashCode() {
        return Objects.hash(inputAssessments, ouputAssessments);
    }

    @Override
    public String toString() {
        return "GuardrailAssessmentSummary{" + "inputAssessments=" + inputAssessments + ", ouputAssessments="
                + ouputAssessments + '}';
    }

    public static class Builder {
        private List<GuardrailAssessment> inputAssessments;
        private List<GuardrailAssessment> ouputAssessments;

        public Builder inputAssessments(List<GuardrailAssessment> inputAssessments) {
            this.inputAssessments = inputAssessments;
            return this;
        }

        public Builder ouputAssessments(List<GuardrailAssessment> ouputAssessments) {
            this.ouputAssessments = ouputAssessments;
            return this;
        }

        public GuardrailAssessmentSummary build() {
            return new GuardrailAssessmentSummary(this);
        }
    }
}
