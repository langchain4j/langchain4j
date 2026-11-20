package dev.langchain4j.internal;

import static dev.langchain4j.internal.Exceptions.restoreInterruptIfInterrupted;
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

    /**
     * Calls an asynchronous operation so that the caller never sees it throw, which is the rule for every method that
     * returns a {@link CompletableFuture}: anything the operation throws, including an {@link Error}, is returned as a
     * failed future, and a {@code null} future as a future failed with an {@link IllegalStateException}.
     * <p>
     * Use {@link #failOnThrow(Object, String, Supplier)} when the operation can return {@code null}, so that the
     * message names the method that did.
     *
     * @param operation the operation that returns the future
     * @return the future returned by the operation, or a failed future
     */
    public static <T> CompletableFuture<T> failOnThrow(Supplier<CompletableFuture<T>> operation) {
        return failOnThrow(operation, () -> "The asynchronous operation");
    }

    /**
     * Like {@link #failOnThrow(Supplier)}, but a {@code null} future fails with an {@link IllegalStateException} that
     * names the method that returned it: the class name of {@code target}, then {@code method}, for example
     * {@code "<class name of target>.doChatAsync(ChatRequest) returned null"}.
     *
     * @param target    the object whose method the operation calls
     * @param method    the method the operation calls, for example {@code "doChatAsync(ChatRequest)"}
     * @param operation the operation that returns the future
     * @return the future returned by the operation, or a failed future
     */
    public static <T> CompletableFuture<T> failOnThrow(
            Object target, String method, Supplier<CompletableFuture<T>> operation) {
        return failOnThrow(operation, () -> target.getClass().getName() + "." + method);
    }

    private static <T> CompletableFuture<T> failOnThrow(
            Supplier<CompletableFuture<T>> operation, Supplier<String> name) {
        CompletableFuture<T> future;
        try {
            future = operation.get();
        } catch (Throwable error) {
            restoreInterruptIfInterrupted(error);
            return CompletableFuture.failedFuture(error);
        }
        if (future == null) {
            return CompletableFuture.failedFuture(new IllegalStateException(name.get() + " returned null"));
        }
        return future;
    }
}
