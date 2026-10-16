package dev.langchain4j.service;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.model.chat.response.StreamingHandle;

/**
 * Decorates a {@link StreamingHandle} so that an action runs when the stream is canceled through it.
 * The action may run more than once if {@link #cancel()} is called more than once, so it must be idempotent.
 */
final class InvocationEndingStreamingHandle implements StreamingHandle {

    private final StreamingHandle delegate;
    private final Runnable onCancel;

    InvocationEndingStreamingHandle(StreamingHandle delegate, Runnable onCancel) {
        this.delegate = ensureNotNull(delegate, "delegate");
        this.onCancel = ensureNotNull(onCancel, "onCancel");
    }

    @Override
    public void cancel() {
        this.delegate.cancel();
        this.onCancel.run();
    }

    @Override
    public boolean isCancelled() {
        return this.delegate.isCancelled();
    }
}
