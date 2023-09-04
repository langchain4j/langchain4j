package dev.langchain4j.model.output;

import java.util.Objects;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

public class TokenUsage {

    private final Integer inputTokenCount;
    private final Integer outputTokenCount;
    private final Integer totalTokenCount;

    public TokenUsage(Integer inputTokenCount) {
        this(inputTokenCount, null);
    }

    public TokenUsage(Integer inputTokenCount, Integer outputTokenCount) {
        this(inputTokenCount, outputTokenCount, inputTokenCount + getOrDefault(outputTokenCount, 0));
    }

    public TokenUsage(Integer inputTokenCount, Integer outputTokenCount, Integer totalTokenCount) {
        this.inputTokenCount = ensureNotNull(inputTokenCount, "inputTokenCount");
        this.outputTokenCount = outputTokenCount;
        this.totalTokenCount = ensureNotNull(totalTokenCount, "totalTokenCount");
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TokenUsage that = (TokenUsage) o;
        return Objects.equals(this.inputTokenCount, that.inputTokenCount)
                && Objects.equals(this.outputTokenCount, that.outputTokenCount);
    }

    @Override
    public int hashCode() {
        return Objects.hash(inputTokenCount, outputTokenCount);
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
