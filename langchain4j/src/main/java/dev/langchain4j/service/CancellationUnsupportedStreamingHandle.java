package dev.langchain4j.service;

import dev.langchain4j.Internal;
import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.model.chat.response.StreamingHandle;

/**
 * @since 1.8.0
 */
@Internal
class CancellationUnsupportedStreamingHandle implements StreamingHandle {

    @Override
    public void cancel() {
        throw new UnsupportedFeatureException("Streaming cancellation is not supported by this " +
                "StreamingChatModel implementation. It should invoke " +
                "StreamingChatResponseHandler.onPartialResponse(PartialResponse, PartialResponseContext) " +
                "instead of StreamingChatResponseHandler.onPartialResponse(String).");
    }

    @Override
    public boolean isCancelled() {
        return false;
    }
}
