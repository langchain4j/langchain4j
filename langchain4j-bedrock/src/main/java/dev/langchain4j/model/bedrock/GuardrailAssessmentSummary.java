package dev.langchain4j.model.bedrock;

import java.util.List;
import java.util.Objects;

public class GuardrailAssessmentSummary {
    private final List<GuardrailAssessment> inputAssessments;
    private final List<GuardrailAssessment> outputAssessments;

    public GuardrailAssessmentSummary(Builder builder) {
        this.inputAssessments = builder.inputAssessments;
        this.outputAssessments = builder.outputAssessments;
    }

    public List<GuardrailAssessment> inputAssessments() {
        return inputAssessments;
    }

    public List<GuardrailAssessment> outputAssessments() {
        return outputAssessments;
    }

    @Deprecated
    public List<GuardrailAssessment> ouputAssessments() {
        return outputAssessments();
    }

    public boolean hasAssessments() {
        return (inputAssessments != null && !inputAssessments.isEmpty())
                || (outputAssessments != null && !outputAssessments.isEmpty());
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
                && Objects.equals(outputAssessments, that.outputAssessments);
    }

    @Override
    public int hashCode() {
        return Objects.hash(inputAssessments, outputAssessments);
    }

    @Override
    public String toString() {
        return "GuardrailAssessmentSummary{" + "inputAssessments=" + inputAssessments + ", outputAssessments="
                + outputAssessments + '}';
    }

    public static class Builder {
        private List<GuardrailAssessment> inputAssessments;
        private List<GuardrailAssessment> outputAssessments;

        public Builder inputAssessments(List<GuardrailAssessment> inputAssessments) {
            this.inputAssessments = inputAssessments;
            return this;
        }

        public Builder outputAssessments(List<GuardrailAssessment> outputAssessments) {
            this.outputAssessments = outputAssessments;
            return this;
        }

        @Deprecated
        public Builder ouputAssessments(List<GuardrailAssessment> ouputAssessments) {
            return outputAssessments(ouputAssessments);
        }

        public GuardrailAssessmentSummary build() {
            return new GuardrailAssessmentSummary(this);
        }
    }
}
