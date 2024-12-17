package dev.langchain4j.model.openai;

import dev.langchain4j.Experimental;
import dev.langchain4j.model.output.TokenUsage;

import java.util.Objects;

@Experimental
public class OpenAiTokenUsage extends TokenUsage {

    @Experimental
    public record InputTokensDetails(
            Integer cachedTokens
    ) {
    }

    @Experimental
    public record OutputTokensDetails(
            Integer reasoningTokens
    ) {
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
        return "OpenAiTokenUsage{" +
                " inputTokenCount = " + inputTokenCount() +
                ", inputTokensDetails=" + inputTokensDetails +
                ", outputTokenCount = " + outputTokenCount() +
                ", outputTokensDetails=" + outputTokensDetails +
                ", totalTokenCount = " + totalTokenCount() +
                '}';
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
