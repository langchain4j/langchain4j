package dev.langchain4j.model.openaiofficial;

import dev.langchain4j.model.output.TokenUsage;

import java.util.Objects;

public class OpenAiOfficialTokenUsage extends TokenUsage {

    private final InputTokensDetails inputTokensDetails;
    private final OutputTokensDetails outputTokensDetails;

    private OpenAiOfficialTokenUsage(Builder builder) {
        super(builder.inputTokenCount, builder.outputTokenCount, builder.totalTokenCount);
        this.inputTokensDetails = builder.inputTokensDetails;
        this.outputTokensDetails = builder.outputTokensDetails;
    }

    public InputTokensDetails inputTokensDetails() {
        return inputTokensDetails;
    }

    public OutputTokensDetails outputTokensDetails() {
        return outputTokensDetails;
    }

    @Override
    public OpenAiOfficialTokenUsage add(TokenUsage that) {
        if (that == null) {
            return this;
        }

        return builder()
                .inputTokenCount(sum(this.inputTokenCount(), that.inputTokenCount()))
                .inputTokensDetails(addInputTokenDetails(that))
                .outputTokenCount(sum(this.outputTokenCount(), that.outputTokenCount()))
                .outputTokensDetails(addOutputTokensDetails(that))
                .totalTokenCount(sum(this.totalTokenCount(), that.totalTokenCount()))
                .build();
    }

    private InputTokensDetails addInputTokenDetails(TokenUsage that) {
        if (that instanceof OpenAiOfficialTokenUsage thatOpenAiTokenUsage) {
            if (this.inputTokensDetails == null) {
                return thatOpenAiTokenUsage.inputTokensDetails;
            } else if (thatOpenAiTokenUsage.inputTokensDetails == null) {
                return this.inputTokensDetails;
            } else {
                return InputTokensDetails.builder()
                        .cachedTokens(sum(this.inputTokensDetails.cachedTokens, thatOpenAiTokenUsage.inputTokensDetails.cachedTokens))
                        .build();
            }
        } else {
            return this.inputTokensDetails;
        }
    }

    private OutputTokensDetails addOutputTokensDetails(TokenUsage that) {
        if (that instanceof OpenAiOfficialTokenUsage thatOpenAiTokenUsage) {
            if (this.outputTokensDetails == null) {
                return thatOpenAiTokenUsage.outputTokensDetails;
            } else if (thatOpenAiTokenUsage.outputTokensDetails == null) {
                return this.outputTokensDetails;
            } else {
                return OutputTokensDetails.builder()
                        .reasoningTokens(sum(this.outputTokensDetails.reasoningTokens, thatOpenAiTokenUsage.outputTokensDetails.reasoningTokens))
                        .build();
            }
        } else {
            return this.outputTokensDetails;
        }
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

        private Integer inputTokenCount;
        private InputTokensDetails inputTokensDetails;
        private Integer outputTokenCount;
        private OutputTokensDetails outputTokensDetails;
        private Integer totalTokenCount;

        public Builder inputTokenCount(Integer inputTokenCount) {
            this.inputTokenCount = inputTokenCount;
            return this;
        }

        public Builder inputTokenCount(Long inputTokenCount) {
            if (inputTokenCount != null) {
                this.inputTokenCount = inputTokenCount.intValue();
            }
            return this;
        }

        public Builder inputTokensDetails(InputTokensDetails inputTokensDetails) {
            this.inputTokensDetails = inputTokensDetails;
            return this;
        }

        public Builder outputTokenCount(Integer outputTokenCount) {
            this.outputTokenCount = outputTokenCount;
            return this;
        }

        public Builder outputTokenCount(Long outputTokenCount) {
            if (outputTokenCount != null) {
                this.outputTokenCount = outputTokenCount.intValue();
            }
            return this;
        }

        public Builder outputTokensDetails(OutputTokensDetails outputTokensDetails) {
            this.outputTokensDetails = outputTokensDetails;
            return this;
        }

        public Builder totalTokenCount(Integer totalTokenCount) {
            this.totalTokenCount = totalTokenCount;
            return this;
        }

        public Builder totalTokenCount(Long totalTokenCount) {
            if (totalTokenCount != null) {
                this.totalTokenCount = totalTokenCount.intValue();
            }
            return this;
        }

        public OpenAiOfficialTokenUsage build() {
            return new OpenAiOfficialTokenUsage(this);
        }
    }

    public static class InputTokensDetails {

        private final Integer cachedTokens;

        public InputTokensDetails(Builder builder) {
            this.cachedTokens = builder.cachedTokens;
        }

        public Integer cachedTokens() {
            return cachedTokens;
        }

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {

            private Integer cachedTokens;

            public Builder cachedTokens(Integer cachedTokens) {
                this.cachedTokens = cachedTokens;
                return this;
            }

            public Builder cachedTokens(Long cachedTokens) {
                if (cachedTokens != null) {
                    this.cachedTokens = cachedTokens.intValue();
                }
                return this;
            }

            public InputTokensDetails build() {
                return new InputTokensDetails(this);
            }
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

    public static class OutputTokensDetails {

        private final Integer reasoningTokens;

        public OutputTokensDetails(Builder builder) {
            this.reasoningTokens = builder.reasoningTokens;
        }

        public Integer reasoningTokens() {
            return reasoningTokens;
        }

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {

            private Integer reasoningTokens;

            public Builder reasoningTokens(Integer reasoningTokens) {
                this.reasoningTokens = reasoningTokens;
                return this;
            }

            public Builder reasoningTokens(Long reasoningTokens) {
                if (reasoningTokens != null) {
                    this.reasoningTokens = reasoningTokens.intValue();
                }
                return this;
            }

            public OutputTokensDetails build() {
                return new OutputTokensDetails(this);
            }
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
}
