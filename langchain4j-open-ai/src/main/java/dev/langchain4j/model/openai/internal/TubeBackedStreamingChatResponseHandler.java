package dev.langchain4j.model.openai.internal;

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
import dev.langchain4j.model.chat.response.StreamingEvent;
import dev.langchain4j.model.chat.response.StreamingHandle;
import mutiny.zero.Tube;

/**
 * Bridge from the handler-based {@link StreamingChatResponseHandler} contract to a
 * {@link Tube} of {@link StreamingEvent}s. Lets the same dispatch logic that drives the
 * handler-based API drive the publisher-based API, with no duplicated event-mapping code.
 * <p>
 * Each handler callback maps to a {@code tube.send(event)}; {@code onCompleteResponse} also
 * terminates the tube; {@code onError} fails it. Calls after cancellation are silently dropped.
 */
@Internal
public final class TubeBackedStreamingChatResponseHandler implements StreamingChatResponseHandler {

    private final Tube<StreamingEvent> tube;
    private final StreamingHandle streamingHandle;

    public TubeBackedStreamingChatResponseHandler(Tube<StreamingEvent> tube) {
        this.tube = tube;
        // The dispatcher constructs PartialResponseContext etc. with this handle (it must be
        // non-null per their constructors). isCancelled() reflects the tube's state; cancel() is a
        // no-op — publisher-path consumers use Flow.Subscription.cancel() instead, which propagates
        // through tube.whenCancelled(...).
        this.streamingHandle = new StreamingHandle() {
            @Override public void cancel() { /* no-op for publisher path */ }
            @Override public boolean isCancelled() { return tube.cancelled(); }
        };
    }

    public StreamingHandle streamingHandle() {
        return streamingHandle;
    }

    @Override
    public void onPartialResponse(PartialResponse partialResponse, PartialResponseContext context) {
        if (!tube.cancelled()) {
            tube.send(partialResponse);
        }
    }

    @Override
    public void onPartialThinking(PartialThinking partialThinking, PartialThinkingContext context) {
        if (!tube.cancelled()) {
            tube.send(partialThinking);
        }
    }

    @Override
    public void onPartialToolCall(PartialToolCall partialToolCall, PartialToolCallContext context) {
        if (!tube.cancelled()) {
            tube.send(partialToolCall);
        }
    }

    @Override
    public void onCompleteToolCall(CompleteToolCall completeToolCall) {
        if (!tube.cancelled()) {
            tube.send(completeToolCall);
        }
    }

    @Override
    public void onCompleteResponse(ChatResponse completeResponse) {
        if (!tube.cancelled()) {
            tube.send(completeResponse);
            tube.complete();
        }
    }

    @Override
    public void onError(Throwable error) {
        if (!tube.cancelled()) {
            tube.fail(error);
        }
    }
}
