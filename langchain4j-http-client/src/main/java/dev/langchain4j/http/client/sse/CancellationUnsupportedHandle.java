package dev.langchain4j.http.client.sse;

import dev.langchain4j.Internal;
import dev.langchain4j.exception.UnsupportedFeatureException;

/**
 * @since 1.8.0
 */
@Internal
public class CancellationUnsupportedHandle implements ServerSentEventParsingHandle {

    @Override
    public void cancel() {
        throw new UnsupportedFeatureException("Streaming cancellation is not supported when calling " +
                "ServerSentEventListener.onEvent(ServerSentEvent). Please call " +
                "ServerSentEventListener.onEvent(ServerSentEvent, ServerSentEventContext) instead.");
    }

    @Override
    public boolean isCancelled() {
        return false;
    }
}
