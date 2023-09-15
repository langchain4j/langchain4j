package dev.langchain4j.model.output;

import java.util.Objects;

import static dev.langchain4j.internal.Utils.getOrDefault;

public class TokenUsage {

    private final Integer inputTokenCount;
    private final Integer outputTokenCount;
    private final Integer totalTokenCount;

    public TokenUsage() {
        this(null);
    }

    public TokenUsage(Integer inputTokenCount) {
        this(inputTokenCount, null);
    }

    public TokenUsage(Integer inputTokenCount, Integer outputTokenCount) {
        this(inputTokenCount, outputTokenCount, sum(inputTokenCount, outputTokenCount));
    }

    public TokenUsage(Integer inputTokenCount, Integer outputTokenCount, Integer totalTokenCount) {
        this.inputTokenCount = inputTokenCount;
        this.outputTokenCount = outputTokenCount;
        this.totalTokenCount = totalTokenCount;
    }

    public Integer inputTokenCount() {
        return inputTokenCount;
    }

    public Integer outputTokenCount() {
        return outputTokenCount;
    }

    public Integer totalTokenCount() {
        return totalTokenCount;
    }

    public TokenUsage add(TokenUsage that) {
        return new TokenUsage(
                sum(this.inputTokenCount, that.inputTokenCount),
                sum(this.outputTokenCount, that.outputTokenCount),
                sum(this.totalTokenCount, that.totalTokenCount)
        );
    }

    private static Integer sum(Integer first, Integer second) {
        if (first == null && second == null) {
            return null;
        }

        return getOrDefault(first, 0) + getOrDefault(second, 0);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TokenUsage that = (TokenUsage) o;
        return Objects.equals(this.inputTokenCount, that.inputTokenCount)
                && Objects.equals(this.outputTokenCount, that.outputTokenCount)
                && Objects.equals(this.totalTokenCount, that.totalTokenCount);
    }

    @Override
    public int hashCode() {
        return Objects.hash(inputTokenCount, outputTokenCount, totalTokenCount);
    }

    @Override
    public String toString() {
        return "TokenUsage {" +
                " inputTokenCount = " + inputTokenCount +
                ", outputTokenCount = " + outputTokenCount +
                ", totalTokenCount = " + totalTokenCount +
                " }";
    }
}
