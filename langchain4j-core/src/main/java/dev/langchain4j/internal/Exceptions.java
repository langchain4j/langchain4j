package dev.langchain4j.internal;

import dev.langchain4j.Internal;

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
}
