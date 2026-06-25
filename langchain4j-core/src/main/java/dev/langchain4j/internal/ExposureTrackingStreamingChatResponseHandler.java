package dev.langchain4j.internal;

import dev.langchain4j.Internal;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.CompleteToolCall;
import dev.langchain4j.model.chat.response.PartialResponse;
import dev.langchain4j.model.chat.response.PartialResponseContext;
import dev.langchain4j.model.chat.response.PartialThinking;
import dev.langchain4j.model.chat.response.PartialThinkingContext;
import dev.langchain4j.model.chat.response.PartialToolCall;
import dev.langchain4j.model.chat.response.PartialToolCallContext;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;

/**
 * Delegating {@link StreamingChatResponseHandler} that records whether the underlying handler received
 * any typed, user-facing callback (partial response/thinking/tool call, complete tool call, complete response
 * or error) while processing a single provider streaming event.
 * <p>
 * Providers use this to decide whether to additionally surface a raw event via
 * {@link StreamingChatResponseHandler#onUnmappedRawEvent(Object)}: a raw event should only be emitted for events that
 * are <b>not</b> already exposed to the user through one of the typed callbacks. Forwarding a raw event (via
 * {@link #onUnmappedRawEvent(Object)}) does not count as exposure.
 * <p>
 * Not thread-safe: it assumes a provider processes streaming events one at a time and calls
 * {@link #resetExposureTracking()} before each event.
 */
@Internal
public class ExposureTrackingStreamingChatResponseHandler implements StreamingChatResponseHandler {

    private final StreamingChatResponseHandler delegate;
    private boolean exposed;

    public ExposureTrackingStreamingChatResponseHandler(StreamingChatResponseHandler delegate) {
        this.delegate = delegate;
    }

    /**
     * Resets exposure tracking. Must be called before processing each provider streaming event.
     */
    public void resetExposureTracking() {
        this.exposed = false;
    }

    /**
     * @return {@code true} if a typed, user-facing callback was invoked since the last
     * {@link #resetExposureTracking()}.
     */
    public boolean wasExposed() {
        return exposed;
    }

    @Override
    public void onPartialResponse(String partialResponse) {
        exposed = true;
        delegate.onPartialResponse(partialResponse);
    }

    @Override
    public void onPartialResponse(PartialResponse partialResponse, PartialResponseContext context) {
        exposed = true;
        delegate.onPartialResponse(partialResponse, context);
    }

    @Override
    public void onPartialThinking(PartialThinking partialThinking) {
        exposed = true;
        delegate.onPartialThinking(partialThinking);
    }

    @Override
    public void onPartialThinking(PartialThinking partialThinking, PartialThinkingContext context) {
        exposed = true;
        delegate.onPartialThinking(partialThinking, context);
    }

    @Override
    public void onPartialToolCall(PartialToolCall partialToolCall) {
        exposed = true;
        delegate.onPartialToolCall(partialToolCall);
    }

    @Override
    public void onPartialToolCall(PartialToolCall partialToolCall, PartialToolCallContext context) {
        exposed = true;
        delegate.onPartialToolCall(partialToolCall, context);
    }

    @Override
    public void onCompleteToolCall(CompleteToolCall completeToolCall) {
        exposed = true;
        delegate.onCompleteToolCall(completeToolCall);
    }

    @Override
    public void onUnmappedRawEvent(Object rawEvent) {
        delegate.onUnmappedRawEvent(rawEvent);
    }

    @Override
    public void onCompleteResponse(ChatResponse completeResponse) {
        exposed = true;
        delegate.onCompleteResponse(completeResponse);
    }

    @Override
    public void onError(Throwable error) {
        exposed = true;
        delegate.onError(error);
    }
}
