package dev.langchain4j.model.googleai;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
class GeminiCodeExecutionResult {
    private GeminiOutcome outcome; //TODO how to deal with the non-OK outcomes?
    private String output;

    @JsonCreator
    GeminiCodeExecutionResult(@JsonProperty("outcome") GeminiOutcome outcome, @JsonProperty("output") String output) {
        this.outcome = outcome;
        this.output = output;
    }

    public static GeminiCodeExecutionResultBuilder builder() {
        return new GeminiCodeExecutionResultBuilder();
    }

    public GeminiOutcome getOutcome() {
        return this.outcome;
    }

    public String getOutput() {
        return this.output;
    }

    public void setOutcome(GeminiOutcome outcome) {
        this.outcome = outcome;
    }

    public void setOutput(String output) {
        this.output = output;
    }

    public boolean equals(final Object o) {
        if (o == this) return true;
        if (!(o instanceof GeminiCodeExecutionResult)) return false;
        final GeminiCodeExecutionResult other = (GeminiCodeExecutionResult) o;
        if (!other.canEqual((Object) this)) return false;
        final Object this$outcome = this.getOutcome();
        final Object other$outcome = other.getOutcome();
        if (this$outcome == null ? other$outcome != null : !this$outcome.equals(other$outcome)) return false;
        final Object this$output = this.getOutput();
        final Object other$output = other.getOutput();
        if (this$output == null ? other$output != null : !this$output.equals(other$output)) return false;
        return true;
    }

    protected boolean canEqual(final Object other) {
        return other instanceof GeminiCodeExecutionResult;
    }

    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        final Object $outcome = this.getOutcome();
        result = result * PRIME + ($outcome == null ? 43 : $outcome.hashCode());
        final Object $output = this.getOutput();
        result = result * PRIME + ($output == null ? 43 : $output.hashCode());
        return result;
    }

    public String toString() {
        return "GeminiCodeExecutionResult(outcome=" + this.getOutcome() + ", output=" + this.getOutput() + ")";
    }

    public static class GeminiCodeExecutionResultBuilder {
        private GeminiOutcome outcome;
        private String output;

        GeminiCodeExecutionResultBuilder() {
        }

        public GeminiCodeExecutionResultBuilder outcome(GeminiOutcome outcome) {
            this.outcome = outcome;
            return this;
        }

        public GeminiCodeExecutionResultBuilder output(String output) {
            this.output = output;
            return this;
        }

        public GeminiCodeExecutionResult build() {
            return new GeminiCodeExecutionResult(this.outcome, this.output);
        }

        public String toString() {
            return "GeminiCodeExecutionResult.GeminiCodeExecutionResultBuilder(outcome=" + this.outcome + ", output=" + this.output + ")";
        }
    }
}
