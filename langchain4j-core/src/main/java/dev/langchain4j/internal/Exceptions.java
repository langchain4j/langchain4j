package dev.langchain4j.internal;

import dev.langchain4j.Internal;

import java.util.concurrent.Callable;

/**
 * Utility methods for creating common exceptions.
 */
@Internal
public class Exceptions {

    private Exceptions() {}

    /**
     * Constructs an {@link IllegalArgumentException} with the given formatted result.
     *
     * <p>Equivalent to {@code new IllegalArgumentException(String.format(format, args))}.
     *
     * @param format the format string
     * @param args the format arguments
     * @return the constructed exception.
     */
    public static IllegalArgumentException illegalArgument(String format, Object... args) {
        return new IllegalArgumentException(format.formatted(args));
    }

    /**
     * Constructs an {@link RuntimeException} with the given formatted result.
     *
     * <p>Equivalent to {@code new RuntimeException(String.format(format, args))}.
     *
     * @param format the format string
     * @param args the format arguments
     * @return the constructed exception.
     */
    public static RuntimeException runtime(String format, Object... args) {
        return new RuntimeException(format.formatted(args));
    }

    public static Throwable unwrapRuntimeException(Exception e) {
        if (e.getClass() == RuntimeException.class && e.getCause() != null) {
            // when a checked exception is wrapped into a bare RuntimeException, so that callers see
            // the original. A typed exception such as JsonReadException is left alone: its type is
            // the information, and unwrapping it would hand the caller back the JSON library's own
            // exception, which is exactly what a swappable codec must not expose.
            return e.getCause();
        } else {
            return e;
        }
    }

    public static <T> T unchecked(Callable<T> callable) {
        try {
            return callable.call();
        } catch (Exception e) {
            if (e instanceof RuntimeException re) {
                throw re;
            } else {
                throw new RuntimeException(e);
            }
        }
    }
}
