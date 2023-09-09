package dev.langchain4j.model.output;

import java.util.Objects;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

public class Result<T> {

    private final T result;
    private final TokenUsage tokenUsage;
    private final FinishReason finishReason;

    public Result(T result) {
        this(result, null, null);
    }

    public Result(T result, TokenUsage tokenUsage, FinishReason finishReason) {
        this.result = ensureNotNull(result, "result");
        this.tokenUsage = tokenUsage;
        this.finishReason = finishReason;
    }

    public T get() {
        return result;
    }

    public TokenUsage tokenUsage() {
        return tokenUsage;
    }

    public FinishReason finishReason() {
        return finishReason;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Result<?> that = (Result<?>) o;
        return Objects.equals(this.result, that.result)
                && Objects.equals(this.tokenUsage, that.tokenUsage)
                && Objects.equals(this.finishReason, that.finishReason);
    }

    @Override
    public int hashCode() {
        return Objects.hash(result, tokenUsage, finishReason);
    }

    @Override
    public String toString() {
        return "Result {" +
                " result = " + result +
                ", tokenUsage = " + tokenUsage +
                ", finishReason = " + finishReason +
                " }";
    }

    public static <T> Result<T> from(T result) {
        return new Result<>(result);
    }

    public static <T> Result<T> from(T result, TokenUsage tokenUsage) {
        return new Result<>(result, tokenUsage, null);
    }

    public static <T> Result<T> from(T result, TokenUsage tokenUsage, FinishReason finishReason) {
        return new Result<>(result, tokenUsage, finishReason);
    }
}
