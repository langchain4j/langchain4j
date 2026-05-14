package dev.langchain4j.model.googleai;

import dev.langchain4j.model.output.TokenUsage;
import java.util.Objects;

public class GoogleAiGeminiTokenUsage extends TokenUsage {

    private final Integer cachedContentTokenCount;
    private final Integer thoughtsTokenCount;

    private GoogleAiGeminiTokenUsage(Builder builder) {
        super(builder.inputTokenCount, builder.outputTokenCount, builder.totalTokenCount);
        this.cachedContentTokenCount = builder.cachedContentTokenCount;
        this.thoughtsTokenCount = builder.thoughtsTokenCount;
    }

    public Integer cachedContentTokenCount() {
        return cachedContentTokenCount;
    }

    public Integer thoughtsTokenCount() {
        return thoughtsTokenCount;
    }

    @Override
    public GoogleAiGeminiTokenUsage add(TokenUsage that) {
        if (that == null) {
            return this;
        }

        return GoogleAiGeminiTokenUsage.builder()
                .inputTokenCount(sum(this.inputTokenCount(), that.inputTokenCount()))
                .outputTokenCount(sum(this.outputTokenCount(), that.outputTokenCount()))
                .totalTokenCount(sum(this.totalTokenCount(), that.totalTokenCount()))
                .cachedContentTokenCount(addCachedContentTokenCount(that))
                .thoughtsTokenCount(addThoughtsTokenCount(that))
                .build();
    }

    private Integer addCachedContentTokenCount(TokenUsage that) {
        if (that instanceof GoogleAiGeminiTokenUsage thatGeminiTokenUsage) {
            return sum(this.cachedContentTokenCount, thatGeminiTokenUsage.cachedContentTokenCount);
        }
        return this.cachedContentTokenCount;
    }

    private Integer addThoughtsTokenCount(TokenUsage that) {
        if (that instanceof GoogleAiGeminiTokenUsage thatGeminiTokenUsage) {
            return sum(this.thoughtsTokenCount, thatGeminiTokenUsage.thoughtsTokenCount);
        }
        return this.thoughtsTokenCount;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        GoogleAiGeminiTokenUsage that = (GoogleAiGeminiTokenUsage) o;
        return Objects.equals(cachedContentTokenCount, that.cachedContentTokenCount)
                && Objects.equals(thoughtsTokenCount, that.thoughtsTokenCount);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), cachedContentTokenCount, thoughtsTokenCount);
    }

    @Override
    public String toString() {
        return "GoogleAiGeminiTokenUsage {" + " inputTokenCount = "
                + inputTokenCount() + ", outputTokenCount = "
                + outputTokenCount() + ", totalTokenCount = "
                + totalTokenCount() + ", cachedContentTokenCount = "
                + cachedContentTokenCount + ", thoughtsTokenCount = "
                + thoughtsTokenCount + " }";
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private Integer inputTokenCount;
        private Integer outputTokenCount;
        private Integer totalTokenCount;
        private Integer cachedContentTokenCount;
        private Integer thoughtsTokenCount;

        public Builder inputTokenCount(Integer inputTokenCount) {
            this.inputTokenCount = inputTokenCount;
            return this;
        }

        public Builder outputTokenCount(Integer outputTokenCount) {
            this.outputTokenCount = outputTokenCount;
            return this;
        }

        public Builder totalTokenCount(Integer totalTokenCount) {
            this.totalTokenCount = totalTokenCount;
            return this;
        }

        public Builder cachedContentTokenCount(Integer cachedContentTokenCount) {
            this.cachedContentTokenCount = cachedContentTokenCount;
            return this;
        }

        public Builder thoughtsTokenCount(Integer thoughtsTokenCount) {
            this.thoughtsTokenCount = thoughtsTokenCount;
            return this;
        }

        public GoogleAiGeminiTokenUsage build() {
            return new GoogleAiGeminiTokenUsage(this);
        }
    }
}
