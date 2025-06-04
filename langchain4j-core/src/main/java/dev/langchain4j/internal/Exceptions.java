package dev.langchain4j.internal;

import dev.langchain4j.Internal;
import org.jspecify.annotations.Nullable;

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
     * @param args   the format arguments
     * @return the constructed exception.
     */
    public static IllegalArgumentException illegalArgument(String format, Object... args) {
        return new IllegalArgumentException(String.format(format, args));
    }

    /**
     * Constructs an {@link RuntimeException} with the given formatted result.
     *
     * <p>Equivalent to {@code new RuntimeException(String.format(format, args))}.
     *
     * @param format the format string
     * @param args   the format arguments
     * @return the constructed exception.
     */
    public static RuntimeException runtime(String format, Object... args) {
        return new RuntimeException(String.format(format, args));
    }

    /**
     * Finds the root cause of the given throwable by traversing the causal chain.
     *
     * @param e the throwable for which to find the root cause
     * @return the root cause of the throwable, or the throwable itself if it has no cause
     */
    public static @Nullable Throwable findRoot(Throwable e) {
        Throwable cause = e.getCause();
        return cause == null || cause == e ? e : findRoot(cause);
    }

    /**
     * Searches through the causal chain of the given throwable to find a cause of the specified type.
     *
     * @param e          the throwable from which the search starts
     * @param causeClass the class of the desired cause
     * @param <T>        the type of the desired cause
     * @return the cause of the specified type if found, or {@code null} if no such cause exists
     */
    public static @Nullable <T extends Throwable> T findCause(Throwable e, Class<T> causeClass) {
        var cause = e;
        while (cause != null) {
            if (causeClass.isInstance(cause)) {
                return (T) cause;
            }
            cause = cause.getCause();
        }
        return null;
    }
}
