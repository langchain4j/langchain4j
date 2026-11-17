package dev.langchain4j.internal;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.Internal;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * Utility methods for working with {@link CompletableFuture}.
 */
@Internal
public class CompletableFutureUtils {

    private CompletableFutureUtils() {}

    /**
     * Forwards cancellation from one future to another.
     * <p>
     * {@link CompletableFuture#cancel(boolean)} does not propagate to the stages a future was derived from,
     * so when {@code from} represents the future handed to a caller and {@code to} represents the underlying
     * work (e.g. an in-flight HTTP request), cancelling {@code from} would otherwise leave {@code to} running.
     * This method bridges that gap: once {@code from} completes, if it was cancelled, {@code to} is cancelled too.
     *
     * @param from the future whose cancellation should be propagated; ignored if {@code null}.
     * @param to   the future to cancel when {@code from} is cancelled.
     */
    public static void propagateCancellation(CompletableFuture<?> from, CompletableFuture<?> to) {
        if (from == null) {
            return;
        }
        from.whenComplete((ignored, error) -> {
            if (from.isCancelled()) {
                to.cancel(true);
            }
        });
    }

    /**
     * Starts asynchronous work, turning an exception thrown while starting it into a failed future, so that a caller
     * that returns a future never throws. When nothing is thrown, the future of the work itself is returned (not a
     * future derived from it), so canceling the returned future cancels the work.
     * <p>
     * Only a {@link RuntimeException} is turned into a failed future: an {@link Error} propagates.
     *
     * @param start starts the work and returns its future; returning {@code null} fails the returned future
     * @param <T>   the type of the result of the work
     * @return the future of the work, or a future failed with the exception thrown while starting it
     * @see #startSafely(Supplier, String)
     */
    public static <T> CompletableFuture<T> startSafely(Supplier<CompletableFuture<T>> start) {
        return startSafely(start, "future");
    }

    /**
     * As {@link #startSafely(Supplier)}, naming the future in the message of the failure when {@code start} returns
     * {@code null}.
     *
     * @param start starts the work and returns its future; returning {@code null} fails the returned future with an
     *              {@link IllegalArgumentException} whose message names it
     * @param name  the name of the future, for example {@code "future of the tool"}
     * @param <T>   the type of the result of the work
     * @return the future of the work, or a future failed with the exception thrown while starting it
     */
    public static <T> CompletableFuture<T> startSafely(Supplier<CompletableFuture<T>> start, String name) {

        try {
            return ensureNotNull(start.get(), name);
        } catch (RuntimeException e) {
            return CompletableFuture.failedFuture(e);
        }
    }
}
