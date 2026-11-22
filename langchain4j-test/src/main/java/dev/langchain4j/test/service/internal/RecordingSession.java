package dev.langchain4j.test.service.internal;

import dev.langchain4j.Internal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * The AI Service invocations captured by one call of the recorder.
 */
@Internal
public final class RecordingSession {

    private final List<InvocationRecord> invocations = new ArrayList<>();
    private final List<InvocationRecord> topLevelInvocations = new ArrayList<>();
    private boolean closed;

    RecordingSession() {}

    synchronized void close() {
        closed = true;
    }

    /**
     * Adds the invocation, unless the recording is closed.
     *
     * @return {@code false} if the recording is closed
     */
    boolean add(InvocationRecord invocation) {
        synchronized (this) {
            if (closed) {
                return false;
            }
            invocations.add(invocation);
            if (invocation.parent() == null) {
                topLevelInvocations.add(invocation);
            } else {
                invocation.parent().addChild(invocation);
            }
            notifyAll();
            return true;
        }
    }

    /**
     * Returns the invocations that were not started by another captured invocation, in the order they started.
     */
    public synchronized List<InvocationRecord> topLevelInvocations() {
        return List.copyOf(topLevelInvocations);
    }

    /**
     * Returns all captured invocations, including those started by another captured invocation.
     */
    public synchronized List<InvocationRecord> invocations() {
        return List.copyOf(invocations);
    }

    public synchronized List<InvocationRecord> unfinishedInvocations() {
        return invocations.stream().filter(invocation -> !invocation.isFinished()).toList();
    }

    public synchronized boolean anyCancelled() {
        return invocations.stream().anyMatch(InvocationRecord::isCancelled);
    }

    /**
     * Waits until all captured invocations are finished or the timeout elapses.
     *
     * @return {@code true} if all invocations are finished
     */
    public synchronized boolean awaitFinished(long timeoutMillis) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        while (!unfinishedInvocations().isEmpty()) {
            long remainingNanos = deadline - System.nanoTime();
            if (remainingNanos <= 0) {
                return false;
            }
            wait(Math.max(1, TimeUnit.NANOSECONDS.toMillis(remainingNanos)));
        }
        return true;
    }
}
