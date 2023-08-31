package dev.langchain4j.model.output;

import java.util.Objects;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

public class Result<T> {

    private final T result;

    public Result(T result) {
        this.result = ensureNotNull(result, "result");
    }

    public T get() {
        return result;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Result<?> that = (Result<?>) o;
        return Objects.equals(this.result, that.result);
    }

    @Override
    public int hashCode() {
        return Objects.hash(result);
    }

    @Override
    public String toString() {
        return "Result {" +
                " result = " + result +
                " }";
    }

    public static <T> Result<T> from(T result) {
        return new Result<>(result);
    }
}
