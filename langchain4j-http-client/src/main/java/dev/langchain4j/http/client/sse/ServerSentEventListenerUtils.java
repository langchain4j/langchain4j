package dev.langchain4j.http.client.sse;

import dev.langchain4j.Internal;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Internal
public class ServerSentEventListenerUtils {

    private static final Logger log = LoggerFactory.getLogger(ServerSentEventListenerUtils.class);

    public static void ignoringExceptions(Runnable runnable) {
        try {
            runnable.run();
        } catch (Exception e) {
            log.warn(
                    "An exception occurred during the invocation of the SSE listener. "
                            + "This exception has been ignored.",
                    e);
        }
    }

    /**
     * Wraps {@code listener} so that at most one of {@link ServerSentEventListener#onClose()} and
     * {@link ServerSentEventListener#onError(Throwable)} is ever delivered to it. {@link ServerSentEventParser#parse}
     * reports a mid-stream failure by calling {@code onError} itself and then returning normally rather than
     * throwing, so an HTTP client that unconditionally calls {@code onClose} right after {@code parse()} returns
     * would otherwise terminate the same request twice.
     */
    public static ServerSentEventListener terminateOnce(ServerSentEventListener listener) {
        AtomicBoolean terminated = new AtomicBoolean(false);
        return new ServerSentEventListener() {

            @Override
            public void onOpen(SuccessfulHttpResponse response) {
                listener.onOpen(response);
            }

            @Override
            public void onEvent(ServerSentEvent event) {
                listener.onEvent(event);
            }

            @Override
            public void onEvent(ServerSentEvent event, ServerSentEventContext context) {
                listener.onEvent(event, context);
            }

            @Override
            public void onError(Throwable throwable) {
                if (terminated.compareAndSet(false, true)) {
                    listener.onError(throwable);
                } else {
                    log.warn("Ignoring onError() after the SSE listener was already terminated.", throwable);
                }
            }

            @Override
            public void onClose() {
                if (terminated.compareAndSet(false, true)) {
                    listener.onClose();
                } else {
                    log.warn("Ignoring onClose() after the SSE listener was already terminated.");
                }
            }
        };
    }
}
