package dev.langchain4j.model.openai;

import dev.langchain4j.Experimental;
import dev.langchain4j.model.output.TokenUsage;

import java.util.Objects;

@Experimental
public class OpenAiTokenUsage extends TokenUsage {

    @Experimental
    public static class InputTokensDetails {

        private final Integer cachedTokens;

        public InputTokensDetails(Integer cachedTokens) {
            this.cachedTokens = cachedTokens;
        }

        public Integer cachedTokens() {
            return cachedTokens;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) return true;
            if (obj == null || obj.getClass() != this.getClass()) return false;
            var that = (InputTokensDetails) obj;
            return Objects.equals(this.cachedTokens, that.cachedTokens);
        }

        @Override
        public int hashCode() {
            return Objects.hash(cachedTokens);
        }

        @Override
        public String toString() {
            return "OpenAiTokenUsage.InputTokensDetails {" +
                    " cachedTokens = " + cachedTokens +
                    " }";
        }
    }

    @Experimental
    public static class OutputTokensDetails {

        private final Integer reasoningTokens;

        public OutputTokensDetails(Integer reasoningTokens) {
            this.reasoningTokens = reasoningTokens;
        }

        public Integer reasoningTokens() {
            return reasoningTokens;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) return true;
            if (obj == null || obj.getClass() != this.getClass()) return false;
            var that = (OutputTokensDetails) obj;
            return Objects.equals(this.reasoningTokens, that.reasoningTokens);
        }

        @Override
        public int hashCode() {
            return Objects.hash(reasoningTokens);
        }

        @Override
        public String toString() {
            return "OpenAiTokenUsage.OutputTokensDetails {" +
                    " reasoningTokens = " + reasoningTokens +
                    " }";
        }
    }

    private final InputTokensDetails inputTokensDetails;
    private final OutputTokensDetails outputTokensDetails;

    private OpenAiTokenUsage(Builder builder) {
        super(builder.inputTokenCount, builder.outputTokenCount, builder.totalTokenCount);
        this.inputTokensDetails = builder.inputTokensDetails;
        this.outputTokensDetails = builder.outputTokensDetails;
    }

    @Experimental
    public InputTokensDetails inputTokensDetails() {
        return inputTokensDetails;
    }

    @Experimental
    public OutputTokensDetails outputTokensDetails() {
        return outputTokensDetails;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        OpenAiTokenUsage that = (OpenAiTokenUsage) o;
        return Objects.equals(inputTokensDetails, that.inputTokensDetails)
                && Objects.equals(outputTokensDetails, that.outputTokensDetails);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), inputTokensDetails, outputTokensDetails);
    }

    @Override
    public String toString() {
        return "OpenAiTokenUsage {" +
                " inputTokenCount = " + inputTokenCount() +
                ", inputTokensDetails = " + inputTokensDetails +
                ", outputTokenCount = " + outputTokenCount() +
                ", outputTokensDetails = " + outputTokensDetails +
                ", totalTokenCount = " + totalTokenCount() +
                " }";
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private Integer inputTokenCount;
        private InputTokensDetails inputTokensDetails;
        private Integer outputTokenCount;
        private OutputTokensDetails outputTokensDetails;
        private Integer totalTokenCount;

        public Builder inputTokenCount(Integer inputTokenCount) {
            this.inputTokenCount = inputTokenCount;
            return this;
        }

        @Experimental
        public Builder inputTokensDetails(InputTokensDetails inputTokensDetails) {
            this.inputTokensDetails = inputTokensDetails;
            return this;
        }

        public Builder outputTokenCount(Integer outputTokenCount) {
            this.outputTokenCount = outputTokenCount;
            return this;
        }

        @Experimental
        public Builder outputTokensDetails(OutputTokensDetails outputTokensDetails) {
            this.outputTokensDetails = outputTokensDetails;
            return this;
        }

        public Builder totalTokenCount(Integer totalTokenCount) {
            this.totalTokenCount = totalTokenCount;
            return this;
        }

        public OpenAiTokenUsage build() {
            return new OpenAiTokenUsage(this);
        }
    }
}
