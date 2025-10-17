package dev.langchain4j.model.openaiofficial;

import java.util.concurrent.Future;

/** Handles cancellation of streaming requests. */
public interface ResponseCancellationHandler {

    /** Returns true if cancellation has been requested. */
    boolean isCancelled();

    /**
     * Starts monitoring for cancellation.
     *
     * @param onCancelled callback invoked when cancellation is detected
     * @return future to stop monitoring, or null if not supported
     */
    Future<?> startMonitoring(Runnable onCancelled);
}
