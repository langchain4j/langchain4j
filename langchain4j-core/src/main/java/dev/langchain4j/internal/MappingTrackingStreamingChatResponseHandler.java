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
 * or error) while processing a single provider streaming event - i.e. whether the event was mapped to a
 * typed callback.
 * <p>
 * Providers use this to decide whether to additionally surface a raw event via
 * {@link StreamingChatResponseHandler#onUnmappedRawEvent(Object)}: a raw event should only be emitted for events
 * that were <b>not</b> mapped to a typed callback. Forwarding a raw event (via {@link #onUnmappedRawEvent(Object)})
 * does not count as a mapping.
 * <p>
 * Not thread-safe: it assumes a provider processes streaming events one at a time and calls
 * {@link #resetMappingTracking()} before each event.
 */
@Internal
public class MappingTrackingStreamingChatResponseHandler implements StreamingChatResponseHandler {

    private final StreamingChatResponseHandler delegate;
    private boolean mapped;

    public MappingTrackingStreamingChatResponseHandler(StreamingChatResponseHandler delegate) {
        this.delegate = delegate;
    }

    /**
     * Resets mapping tracking. Must be called before processing each provider streaming event.
     */
    public void resetMappingTracking() {
        this.mapped = false;
    }

    /**
     * @return {@code true} if a typed, user-facing callback was invoked since the last
     * {@link #resetMappingTracking()}.
     */
    public boolean wasMapped() {
        return mapped;
    }

    @Override
    public void onPartialResponse(String partialResponse) {
        mapped = true;
        delegate.onPartialResponse(partialResponse);
    }

    @Override
    public void onPartialResponse(PartialResponse partialResponse, PartialResponseContext context) {
        mapped = true;
        delegate.onPartialResponse(partialResponse, context);
    }

    @Override
    public void onPartialThinking(PartialThinking partialThinking) {
        mapped = true;
        delegate.onPartialThinking(partialThinking);
    }

    @Override
    public void onPartialThinking(PartialThinking partialThinking, PartialThinkingContext context) {
        mapped = true;
        delegate.onPartialThinking(partialThinking, context);
    }

    @Override
    public void onPartialToolCall(PartialToolCall partialToolCall) {
        mapped = true;
        delegate.onPartialToolCall(partialToolCall);
    }

    @Override
    public void onPartialToolCall(PartialToolCall partialToolCall, PartialToolCallContext context) {
        mapped = true;
        delegate.onPartialToolCall(partialToolCall, context);
    }

    @Override
    public void onCompleteToolCall(CompleteToolCall completeToolCall) {
        mapped = true;
        delegate.onCompleteToolCall(completeToolCall);
    }

    @Override
    public void onUnmappedRawEvent(Object rawEvent) {
        delegate.onUnmappedRawEvent(rawEvent);
    }

    @Override
    public void onCompleteResponse(ChatResponse completeResponse) {
        mapped = true;
        delegate.onCompleteResponse(completeResponse);
    }

    @Override
    public void onError(Throwable error) {
        mapped = true;
        delegate.onError(error);
    }
}
