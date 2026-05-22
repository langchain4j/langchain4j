package dev.langchain4j.service;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class StreamingLoopState {

    private static final Logger LOG = LoggerFactory.getLogger(StreamingLoopState.class);

    enum TerminalState {
        RUNNING,
        CANCELLED_SILENTLY,
        FAILED,
        COMPLETED
    }

    private final Supplier<Boolean> cancellationSupplier;
    private final Queue<Future<?>> pendingToolCalls = new ConcurrentLinkedQueue<>();
    private final AtomicReference<TerminalState> state = new AtomicReference<>(TerminalState.RUNNING);
    private final AtomicBoolean failureReported = new AtomicBoolean(false);

    StreamingLoopState(Supplier<Boolean> cancellationSupplier) {
        this.cancellationSupplier = cancellationSupplier;
    }

    boolean canContinue() {
        pollExternalCancellation();
        return state.get() == TerminalState.RUNNING;
    }

    boolean isTerminal() {
        pollExternalCancellation();
        return state.get() != TerminalState.RUNNING;
    }

    boolean isSilentlyCancelled() {
        pollExternalCancellation();
        return state.get() == TerminalState.CANCELLED_SILENTLY;
    }

    boolean isFailed() {
        return state.get() == TerminalState.FAILED;
    }

    void registerToolCall(Future<?> future) {
        pendingToolCalls.add(future);
        if (isTerminal()) {
            future.cancel(true);
        }
    }

    void cancelSilently() {
        if (state.compareAndSet(TerminalState.RUNNING, TerminalState.CANCELLED_SILENTLY)) {
            cancelPendingToolCalls();
        }
    }

    boolean fail(Throwable ignored) {
        pollExternalCancellation();
        if (state.compareAndSet(TerminalState.RUNNING, TerminalState.FAILED)) {
            cancelPendingToolCalls();
            return true;
        }
        return false;
    }

    void complete() {
        state.compareAndSet(TerminalState.RUNNING, TerminalState.COMPLETED);
    }

    boolean markFailureReported() {
        return failureReported.compareAndSet(false, true);
    }

    private void pollExternalCancellation() {
        if (state.get() != TerminalState.RUNNING || cancellationSupplier == null) {
            return;
        }
        try {
            if (Boolean.TRUE.equals(cancellationSupplier.get())) {
                cancelSilently();
            }
        } catch (Throwable t) {
            LOG.warn("Cancellation supplier threw; treating as not cancelled", t);
        }
    }

    private void cancelPendingToolCalls() {
        for (Future<?> future : pendingToolCalls) {
            if (!future.isDone()) {
                future.cancel(true);
            }
        }
    }
}
