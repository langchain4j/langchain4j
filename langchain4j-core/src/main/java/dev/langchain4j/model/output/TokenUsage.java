package dev.langchain4j.model.output;

import java.util.Objects;

public class TokenUsage {

    private final int inputTokenCount;
    private final int outputTokenCount;
    private final int totalTokenCount;

    public TokenUsage(int inputTokenCount) {
        this(inputTokenCount, 0);
    }

    public TokenUsage(int inputTokenCount, int outputTokenCount) {
        this(inputTokenCount, outputTokenCount, inputTokenCount + outputTokenCount);
    }

    public TokenUsage(int inputTokenCount, int outputTokenCount, int totalTokenCount) {
        this.inputTokenCount = inputTokenCount;
        this.outputTokenCount = outputTokenCount;
        this.totalTokenCount = totalTokenCount;
    }

    public int inputTokenCount() {
        return inputTokenCount;
    }

    public int outputTokenCount() {
        return outputTokenCount;
    }

    public int totalTokenCount() {
        return totalTokenCount;
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
