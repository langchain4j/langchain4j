package dev.langchain4j.internal;

import static dev.langchain4j.internal.Exceptions.restoreInterruptIfInterrupted;
import static dev.langchain4j.internal.InternalStreamingChatResponseHandlerUtils.logIgnoredError;
import static dev.langchain4j.internal.InternalStreamingChatResponseHandlerUtils.logOnErrorFailure;

import dev.langchain4j.Internal;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;

/**
 * Guards one call of {@code StreamingChatModel.chat(request, options, handler)}, so that the model implementation never
 * receives what the handler's {@code onError} throws:
 * <ul>
 *     <li>what {@code onError} throws on the thread that called {@code chat(...)}, before {@code chat(...)} returns, is
 *     recorded and thrown from {@code chat(...)} once the call of the model has ended;</li>
 *     <li>what {@code onError} throws on another thread, or after {@code chat(...)} returned, is logged;</li>
 *     <li>a failure thrown by the call of the model is reported to {@code onError}, unless the handler already received
 *     the complete response or an error: it is then logged.</li>
 * </ul>
 * Create it on the thread that calls {@code chat(...)}, pass the complete response and the errors to the handler
 * through {@link #onCompleteResponse(ChatResponse)} and {@link #onError(Throwable)}, and call the model with
 * {@link #run(Runnable, StreamingChatResponseHandler)}.
 */
@Internal
public final class StreamingChatHandlerGuard {

    private final StreamingChatResponseHandler handler;
    private final Thread callingThread = Thread.currentThread();
    private volatile boolean handlerDone;
    private volatile boolean chatReturned;
    private volatile Throwable onErrorFailure;

    public StreamingChatHandlerGuard(StreamingChatResponseHandler handler) {
        this.handler = handler;
    }

    /**
     * Passes the complete response to the handler. What the handler throws is passed on.
     */
    public void onCompleteResponse(ChatResponse completeResponse) {
        handler.onCompleteResponse(completeResponse);
        handlerDone = true;
    }

    /**
     * Passes the error to the handler. Never throws: what the handler's {@code onError} throws is recorded or logged.
     */
    public void onError(Throwable error) {
        handlerDone = true;
        try {
            handler.onError(error);
        } catch (Throwable thrown) {
            restoreInterruptIfInterrupted(thrown);
            if (Thread.currentThread() != callingThread || chatReturned) {
                logOnErrorFailure(thrown);
            } else if (onErrorFailure == null) {
                onErrorFailure = thrown;
            } else {
                logIgnoredError(thrown);
            }
        }
    }

    /**
     * Runs the call of the model, then throws what the handler's {@code onError} threw on the calling thread meanwhile.
     *
     * @param call     the call of the model
     * @param reportTo the handler whose {@code onError} receives a failure thrown by the call, when the handler did not
     *                 receive the complete response or an error yet; its {@code onError} must not throw
     */
    public void run(Runnable call, StreamingChatResponseHandler reportTo) {
        try {
            call.run();
        } catch (Throwable error) {
            restoreInterruptIfInterrupted(error);
            if (handlerDone) {
                logIgnoredError(error);
            } else {
                reportTo.onError(error);
            }
        } finally {
            chatReturned = true;
        }
        Throwable failure = onErrorFailure;
        if (failure instanceof RuntimeException runtimeException) {
            throw runtimeException;
        }
        if (failure instanceof Error error) {
            throw error;
        }
        if (failure != null) {
            throw new RuntimeException(failure);
        }
    }
}
