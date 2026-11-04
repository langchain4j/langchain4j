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
 * Wraps the handler an AI Service passes to its streaming chat model, so that the handler runs without the
 * {@linkplain CurrentInvocation current invocation}, also when the model calls it on the thread of the call, during
 * the call. The handler calls code of the caller (for example the handlers of a {@code TokenStream}), which is not
 * part of the work of the invocation.
 */
@Internal
public final class HandlerWithoutInvocation implements StreamingChatResponseHandler {

    private final StreamingChatResponseHandler delegate;

    private HandlerWithoutInvocation(StreamingChatResponseHandler delegate) {
        this.delegate = delegate;
    }

    public static StreamingChatResponseHandler wrap(StreamingChatResponseHandler handler) {
        return new HandlerWithoutInvocation(handler);
    }

    @Override
    public void onPartialResponse(String partialResponse) {
        CurrentInvocation.runWithout(() -> delegate.onPartialResponse(partialResponse));
    }

    @Override
    public void onPartialResponse(PartialResponse partialResponse, PartialResponseContext context) {
        CurrentInvocation.runWithout(() -> delegate.onPartialResponse(partialResponse, context));
    }

    @Override
    public void onPartialThinking(PartialThinking partialThinking) {
        CurrentInvocation.runWithout(() -> delegate.onPartialThinking(partialThinking));
    }

    @Override
    public void onPartialThinking(PartialThinking partialThinking, PartialThinkingContext context) {
        CurrentInvocation.runWithout(() -> delegate.onPartialThinking(partialThinking, context));
    }

    @Override
    public void onPartialToolCall(PartialToolCall partialToolCall) {
        CurrentInvocation.runWithout(() -> delegate.onPartialToolCall(partialToolCall));
    }

    @Override
    public void onPartialToolCall(PartialToolCall partialToolCall, PartialToolCallContext context) {
        CurrentInvocation.runWithout(() -> delegate.onPartialToolCall(partialToolCall, context));
    }

    @Override
    public void onCompleteToolCall(CompleteToolCall completeToolCall) {
        CurrentInvocation.runWithout(() -> delegate.onCompleteToolCall(completeToolCall));
    }

    @Override
    public void onUnmappedRawEvent(Object rawEvent) {
        CurrentInvocation.runWithout(() -> delegate.onUnmappedRawEvent(rawEvent));
    }

    @Override
    public void onCompleteResponse(ChatResponse completeResponse) {
        CurrentInvocation.runWithout(() -> delegate.onCompleteResponse(completeResponse));
    }

    @Override
    public void onError(Throwable error) {
        CurrentInvocation.runWithout(() -> delegate.onError(error));
    }
}
