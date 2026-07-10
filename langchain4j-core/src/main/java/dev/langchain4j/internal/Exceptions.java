package dev.langchain4j.internal;

import dev.langchain4j.Internal;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

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

    /**
     * Unwraps the underlying cause of a {@link CompletionException} or {@link ExecutionException}, returning any
     * other throwable unchanged.
     * <p>
     * {@link java.util.concurrent.CompletableFuture} composition wraps failures in a {@link CompletionException},
     * while the blocking accessor {@link java.util.concurrent.Future#get()} wraps them in an {@link ExecutionException}.
     * Neither wrapper is a domain exception - they are artifacts of the asynchronous machinery. This peels those
     * wrappers (including nested layers, e.g. a {@link CompletionException} around an {@link ExecutionException} that
     * leaked in from a blocking {@code Future.get()} bridged into the pipeline) so callers can inspect or map the real
     * exception.
     *
     * @param throwable the throwable to unwrap.
     * @return the innermost cause once no {@link CompletionException}/{@link ExecutionException} wrapper with a
     *         non-null cause remains, otherwise {@code throwable} unchanged.
     */
    public static Throwable unwrapCompletionException(Throwable throwable) {
        Throwable current = throwable;
        while ((current instanceof CompletionException || current instanceof ExecutionException)
                && current.getCause() != null
                && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }

    public static Throwable unwrapRuntimeException(Exception e) {
        if (e.getClass() == RuntimeException.class && e.getCause() != null) {
            // when checked exception (e.g., JsonProcessingException) is wrapped into RuntimeException
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
