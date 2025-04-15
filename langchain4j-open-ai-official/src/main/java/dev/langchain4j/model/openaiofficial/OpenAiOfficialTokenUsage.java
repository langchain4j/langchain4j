package dev.langchain4j.model.openaiofficial;

import dev.langchain4j.Experimental;
import dev.langchain4j.model.output.TokenUsage;
import java.util.Objects;

@Experimental
public class OpenAiOfficialTokenUsage extends TokenUsage {

    @Experimental
    public static class InputTokensDetails {

        private final Long cachedTokens;

        public InputTokensDetails(Long cachedTokens) {
            this.cachedTokens = cachedTokens;
        }

        public Long cachedTokens() {
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
            return "OpenAiOfficialTokenUsage.InputTokensDetails {" +
                    " cachedTokens = " + cachedTokens +
                    " }";
        }
    }

    @Experimental
    public static class OutputTokensDetails {

        private final Long reasoningTokens;

        public OutputTokensDetails(Long reasoningTokens) {
            this.reasoningTokens = reasoningTokens;
        }

        public Long reasoningTokens() {
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
            return "OpenAiOfficialTokenUsage.OutputTokensDetails {" +
                    " reasoningTokens = " + reasoningTokens +
                    " }";
        }
    }

    private final InputTokensDetails inputTokensDetails;
    private final OutputTokensDetails outputTokensDetails;

    private OpenAiOfficialTokenUsage(Builder builder) {
        super(builder.inputTokenCount(), builder.outputTokenCount(), builder.totalTokenCount());
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
        OpenAiOfficialTokenUsage that = (OpenAiOfficialTokenUsage) o;
        return Objects.equals(inputTokensDetails, that.inputTokensDetails)
                && Objects.equals(outputTokensDetails, that.outputTokensDetails);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), inputTokensDetails, outputTokensDetails);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private Long inputTokenCount;
        private InputTokensDetails inputTokensDetails;
        private Long outputTokenCount;
        private OutputTokensDetails outputTokensDetails;
        private Long totalTokenCount;

        public Integer inputTokenCount() {
            if (inputTokenCount == null) {
                return null;
            }
            return inputTokenCount.intValue();
        }

        public Integer outputTokenCount() {
            if (outputTokenCount == null) {
                return null;
            }
            return outputTokenCount.intValue();
        }

        public Integer totalTokenCount() {
            if (totalTokenCount == null) {
                return null;
            }
            return totalTokenCount.intValue();
        }

        public Builder inputTokenCount(Long inputTokenCount) {
            this.inputTokenCount = inputTokenCount;
            return this;
        }

        @Experimental
        public Builder inputTokensDetails(InputTokensDetails inputTokensDetails) {
            this.inputTokensDetails = inputTokensDetails;
            return this;
        }

        public Builder outputTokenCount(Long outputTokenCount) {
            this.outputTokenCount = outputTokenCount;
            return this;
        }

        @Experimental
        public Builder outputTokensDetails(OutputTokensDetails outputTokensDetails) {
            this.outputTokensDetails = outputTokensDetails;
            return this;
        }

        public Builder totalTokenCount(Long totalTokenCount) {
            this.totalTokenCount = totalTokenCount;
            return this;
        }

        public OpenAiOfficialTokenUsage build() {
            return new OpenAiOfficialTokenUsage(this);
        }
    }
}
