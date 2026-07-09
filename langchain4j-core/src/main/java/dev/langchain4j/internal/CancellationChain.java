package dev.langchain4j.internal;

import static dev.langchain4j.internal.CompletableFutureUtils.propagateCancellation;

import dev.langchain4j.Internal;
import java.util.concurrent.CompletableFuture;

/**
 * Propagates cancellation from a single caller-facing "root" future to every downstream future produced while
 * composing an asynchronous, possibly fan-out, pipeline (e.g. the RAG graph: transform &rarr; route &rarr; retrieve
 * over many queries &times; retrievers).
 * <p>
 * {@link CompletableFuture#cancel(boolean)} does not propagate to the stages a future was derived from, so in a deep
 * or wide composition each in-flight leaf must be wired back to the root individually (see
 * {@link CompletableFutureUtils#propagateCancellation(CompletableFuture, CompletableFuture)}). Doing that by hand at
 * every call site is easy to get wrong — a single missed hop leaks an in-flight call that keeps running after the
 * caller cancelled. This class collapses the wiring to one rule: <b>every future that represents real work enters the
 * pipeline through {@link #track(CompletableFuture)}</b>.
 * <p>
 * If the root is <i>already</i> cancelled when a later leaf is tracked (cancellation landed mid-pipeline),
 * {@code propagateCancellation} fires immediately and cancels that leaf on creation, so dynamically-created
 * fan-out branches are covered without maintaining a separate registry.
 * <p>
 * Not thread-safe to construct, but {@link #track(CompletableFuture)} and {@link #cancelled()} may be called from any
 * thread once constructed; they only read the root and register a completion callback on it.
 *
 * @since 1.17.0
 */
@Internal
public final class CancellationChain {

    private final CompletableFuture<?> root;

    /**
     * @param root the caller-facing future whose cancellation should be propagated to every tracked future.
     */
    public CancellationChain(CompletableFuture<?> root) {
        this.root = ensureNotNull(root);
    }

    private static CompletableFuture<?> ensureNotNull(CompletableFuture<?> root) {
        if (root == null) {
            throw new IllegalArgumentException("root must not be null");
        }
        return root;
    }

    /**
     * Registers {@code future} so it is cancelled whenever the root is cancelled, and returns it unchanged for
     * fluent composition.
     *
     * @param future a future representing in-flight work (a leaf I/O call or a composite of such calls).
     * @return the same {@code future}.
     */
    public <T> CompletableFuture<T> track(CompletableFuture<T> future) {
        propagateCancellation(root, future);
        return future;
    }

    /**
     * @return {@code true} if the root future has been cancelled; useful to short-circuit before starting new work.
     */
    public boolean cancelled() {
        return root.isCancelled();
    }
}
