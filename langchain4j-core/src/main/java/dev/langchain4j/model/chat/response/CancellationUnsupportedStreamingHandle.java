package dev.langchain4j.model.chat.response;

import dev.langchain4j.Internal;
import dev.langchain4j.exception.UnsupportedFeatureException;

@Internal
public class CancellationUnsupportedStreamingHandle implements StreamingHandle {

    @Override
    public void cancel() {
        throw new UnsupportedFeatureException("Streaming cancellation is not supported when calling " +
                "ServerSentEventListener.onEvent(ServerSentEvent). Please call " +
                "ServerSentEventListener.onEvent(ServerSentEvent, StreamingHandle) instead.");
    }

    @Override
    public boolean isCancelled() {
        return false;
    }
}
