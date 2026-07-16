package dev.langchain4j.model.anthropic.internal.client;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.Internal;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.CompleteResponse;
import dev.langchain4j.model.chat.response.CompleteToolCall;
import dev.langchain4j.model.chat.response.PartialResponse;
import dev.langchain4j.model.chat.response.PartialResponseContext;
import dev.langchain4j.model.chat.response.PartialThinking;
import dev.langchain4j.model.chat.response.PartialThinkingContext;
import dev.langchain4j.model.chat.response.PartialToolCall;
import dev.langchain4j.model.chat.response.PartialToolCallContext;
import dev.langchain4j.model.chat.response.RawStreamingEvent;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.chat.response.StreamingEvent;
import dev.langchain4j.model.chat.response.StreamingHandle;
import java.util.concurrent.atomic.AtomicReference;
import mutiny.zero.Tube;

/**
 * Bridge from the handler-based {@link StreamingChatResponseHandler} contract to a
 * {@link Tube} of {@link StreamingEvent}s. Lets the same dispatch logic that drives the
 * handler-based API drive the publisher-based API, with no duplicated event-mapping code.
 * <p>
 * Each handler callback maps to a {@code tube.send(event)}; {@code onCompleteResponse} also
 * terminates the tube; {@code onError} fails it. Calls after cancellation are silently dropped.
 * <p>
 * <b>Cancellation.</b> Unlike the OpenAI module's copy of this class (which cancels at the raw-stream
 * {@code Flow.Subscription} layer), Anthropic streams via {@code httpClient.execute(request, listener)},
 * whose only abort mechanism is the {@link StreamingHandle} that the SSE parser threads through the
 * per-callback contexts. This bridge captures that handle (see {@link #cancelUpstream()}) so that
 * cancelling the downstream subscription tears down the underlying HTTP connection instead of letting it
 * drain in the background. The two copies are pending consolidation into a shared module.
 */
@Internal
public final class TubeBackedStreamingChatResponseHandler implements StreamingChatResponseHandler {

    private final Tube<StreamingEvent> tube;

    // The upstream StreamingHandle only becomes available once the SSE parser has produced its first event, so
    // it is captured lazily from the first per-callback context (see capture(...)).
    private final AtomicReference<StreamingHandle> upstreamHandle = new AtomicReference<>();

    public TubeBackedStreamingChatResponseHandler(Tube<StreamingEvent> tube) {
        this.tube = ensureNotNull(tube, "tube");
    }

    /**
     * Aborts the upstream SSE stream, if it is already running. Wire this to {@code tube.whenTerminates(...)}
     * so a downstream {@code Flow.Subscription} cancellation (or a buffer overflow) tears down the HTTP
     * connection rather than draining it. {@link StreamingHandle#cancel()} is idempotent and a no-op once the
     * stream has ended, so this is safe to call on any terminal signal.
     */
    public void cancelUpstream() {
        StreamingHandle handle = upstreamHandle.get();
        if (handle != null) {
            handle.cancel();
        }
    }

    private void capture(StreamingHandle handle) {
        upstreamHandle.compareAndSet(null, handle);
        if (tube.cancelled()) {
            // Downstream cancelled before we had a handle to abort with (or between capture and the
            // whenTerminates callback): abort now. cancel() is idempotent.
            handle.cancel();
        }
    }

    @Override
    public void onPartialResponse(PartialResponse partialResponse, PartialResponseContext context) {
        capture(context.streamingHandle());
        if (!tube.cancelled()) {
            tube.send(partialResponse);
        }
    }

    @Override
    public void onPartialThinking(PartialThinking partialThinking, PartialThinkingContext context) {
        capture(context.streamingHandle());
        if (!tube.cancelled()) {
            tube.send(partialThinking);
        }
    }

    @Override
    public void onPartialToolCall(PartialToolCall partialToolCall, PartialToolCallContext context) {
        capture(context.streamingHandle());
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
    public void onUnmappedRawEvent(Object rawEvent) {
        if (!tube.cancelled()) {
            tube.send(RawStreamingEvent.of(rawEvent));
        }
    }

    @Override
    public void onCompleteResponse(ChatResponse completeResponse) {
        if (!tube.cancelled()) {
            tube.send(new CompleteResponse(completeResponse));
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
