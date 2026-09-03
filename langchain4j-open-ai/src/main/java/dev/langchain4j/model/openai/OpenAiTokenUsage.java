package dev.langchain4j.model.openai;

import dev.langchain4j.model.output.TokenUsage;
import java.util.Objects;

public class OpenAiTokenUsage extends TokenUsage {

    private final InputTokensDetails inputTokensDetails;
    private final OutputTokensDetails outputTokensDetails;

    private OpenAiTokenUsage(Builder builder) {
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
    public OpenAiTokenUsage add(TokenUsage that) {
        if (that == null) {
            return this;
        }

        return OpenAiTokenUsage.builder()
                .inputTokenCount(sum(this.inputTokenCount(), that.inputTokenCount()))
                .inputTokensDetails(addInputTokenDetails(that))
                .outputTokenCount(sum(this.outputTokenCount(), that.outputTokenCount()))
                .outputTokensDetails(addOutputTokensDetails(that))
                .totalTokenCount(sum(this.totalTokenCount(), that.totalTokenCount()))
                .build();
    }

    private InputTokensDetails addInputTokenDetails(TokenUsage that) {
        if (that instanceof OpenAiTokenUsage thatOpenAiTokenUsage) {
            if (this.inputTokensDetails == null) {
                return thatOpenAiTokenUsage.inputTokensDetails;
            } else if (thatOpenAiTokenUsage.inputTokensDetails == null) {
                return this.inputTokensDetails;
            } else {
                return InputTokensDetails.builder()
                        .cachedTokens(sum(
                                this.inputTokensDetails.cachedTokens,
                                thatOpenAiTokenUsage.inputTokensDetails.cachedTokens))
                        .cacheWriteTokens(sum(
                                this.inputTokensDetails.cacheWriteTokens,
                                thatOpenAiTokenUsage.inputTokensDetails.cacheWriteTokens))
                        .build();
            }
        } else {
            return this.inputTokensDetails;
        }
    }

    private OutputTokensDetails addOutputTokensDetails(TokenUsage that) {
        if (that instanceof OpenAiTokenUsage thatOpenAiTokenUsage) {
            if (this.outputTokensDetails == null) {
                return thatOpenAiTokenUsage.outputTokensDetails;
            } else if (thatOpenAiTokenUsage.outputTokensDetails == null) {
                return this.outputTokensDetails;
            } else {
                return OutputTokensDetails.builder()
                        .reasoningTokens(sum(
                                this.outputTokensDetails.reasoningTokens,
                                thatOpenAiTokenUsage.outputTokensDetails.reasoningTokens))
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
        return "OpenAiTokenUsage {" + " inputTokenCount = "
                + inputTokenCount() + ", inputTokensDetails = "
                + inputTokensDetails + ", outputTokenCount = "
                + outputTokenCount() + ", outputTokensDetails = "
                + outputTokensDetails + ", totalTokenCount = "
                + totalTokenCount() + " }";
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

        public Builder inputTokensDetails(InputTokensDetails inputTokensDetails) {
            this.inputTokensDetails = inputTokensDetails;
            return this;
        }

        public Builder outputTokenCount(Integer outputTokenCount) {
            this.outputTokenCount = outputTokenCount;
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

        public OpenAiTokenUsage build() {
            return new OpenAiTokenUsage(this);
        }
    }

    public static class InputTokensDetails {

        private final Integer cachedTokens;
        private final Integer cacheWriteTokens;

        public InputTokensDetails(Builder builder) {
            this.cachedTokens = builder.cachedTokens;
            this.cacheWriteTokens = builder.cacheWriteTokens;
        }

        public Integer cachedTokens() {
            return cachedTokens;
        }

        /**
         * Returns the number of input tokens written to the prompt cache, as reported by
         * {@code usage.prompt_tokens_details.cache_write_tokens} (Chat Completions API) and
         * {@code usage.input_tokens_details.cache_write_tokens} (Responses API).
         * <p>
         * Returns {@code null} when the model provider did not report it, which is distinct from a
         * reported zero.
         *
         * @since 1.20.0
         */
        public Integer cacheWriteTokens() {
            return cacheWriteTokens;
        }

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {

            private Integer cachedTokens;
            private Integer cacheWriteTokens;

            public Builder cachedTokens(Integer cachedTokens) {
                this.cachedTokens = cachedTokens;
                return this;
            }

            /**
             * @since 1.20.0
             */
            public Builder cacheWriteTokens(Integer cacheWriteTokens) {
                this.cacheWriteTokens = cacheWriteTokens;
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
            return Objects.equals(this.cachedTokens, that.cachedTokens)
                    && Objects.equals(this.cacheWriteTokens, that.cacheWriteTokens);
        }

        @Override
        public int hashCode() {
            return Objects.hash(cachedTokens, cacheWriteTokens);
        }

        @Override
        public String toString() {
            return "OpenAiTokenUsage.InputTokensDetails {" + " cachedTokens = "
                    + cachedTokens + ", cacheWriteTokens = "
                    + cacheWriteTokens + " }";
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
            return "OpenAiTokenUsage.OutputTokensDetails {" + " reasoningTokens = " + reasoningTokens + " }";
        }
    }
}
