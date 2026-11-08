package dev.langchain4j.internal;

import dev.langchain4j.Internal;
import java.util.concurrent.CompletableFuture;

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
}
