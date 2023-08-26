package dev.langchain4j.agent.tool;

import java.util.Objects;
import java.util.function.Function;

@FunctionalInterface
public interface ToolParameterHandler<T, R> extends Function<T, R> {

    default <V> ToolParameterHandler<V, R> compose(ToolParameterHandler<? super V, ? extends T> before) {
        Objects.requireNonNull(before);
        return (V v) -> apply(before.apply(v));
    }

    static <T> ToolParameterHandler<T, T> identity() {
        return t -> t;
    }
}
