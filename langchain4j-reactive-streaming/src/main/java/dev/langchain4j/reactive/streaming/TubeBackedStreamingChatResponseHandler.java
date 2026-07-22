package dev.langchain4j.reactive.streaming;

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
 * Bridge from the handler-based {@link StreamingChatResponseHandler} contract to a {@link Tube} of
 * {@link StreamingEvent}s. It lets the very same dispatch logic that drives the handler-based streaming API drive the
 * reactive publisher-based API, with no duplicated event-mapping code. Shared by all streaming chat model providers.
 * <p>
 * Each handler callback maps to a {@code tube.send(event)}; {@code onCompleteResponse} also terminates the tube;
 * {@code onError} fails it. Calls after cancellation are silently dropped.
 * <p>
 * <b>Cancellation.</b> Two provider wirings are supported:
 * <ul>
 *   <li><b>Subscription-based</b> — providers streaming over {@code HttpClient.stream()} (e.g. OpenAI, Anthropic):
 *       the reactive subscriber cancels its {@code Flow.Subscription} from {@code tube.whenTerminates(...)}. This
 *       bridge only supplies a non-null {@link #streamingHandle()} for dispatchers that must put one in their
 *       per-callback contexts; its {@code cancel()} is a no-op (cancellation goes through the subscription).</li>
 *   <li><b>Handle-based</b> — providers whose only abort mechanism is a {@link StreamingHandle} threaded through the
 *       per-callback contexts (e.g. Bedrock over the AWS SDK): the bridge captures that handle lazily and cancels it
 *       via {@link #cancelUpstream()}, which the provider wires to {@code tube.whenTerminates(...)}.</li>
 * </ul>
 */
@Internal
public final class TubeBackedStreamingChatResponseHandler implements StreamingChatResponseHandler {

    private final Tube<StreamingEvent> tube;

    // For subscription-based providers: a non-null handle to place in per-callback contexts. cancel() is a no-op
    // (they cancel via Flow.Subscription); isCancelled() reflects the tube's state.
    private final StreamingHandle streamingHandle;

    // For handle-based providers: the upstream handle, captured lazily from the first per-callback context that
    // carries a genuine (non-bridge) one.
    private final AtomicReference<StreamingHandle> upstreamHandle = new AtomicReference<>();

    public TubeBackedStreamingChatResponseHandler(Tube<StreamingEvent> tube) {
        this.tube = ensureNotNull(tube, "tube");
        this.streamingHandle = new StreamingHandle() {
            @Override
            public void cancel() {
                // no-op: subscription-based providers cancel via Flow.Subscription
            }

            @Override
            public boolean isCancelled() {
                return tube.cancelled();
            }
        };
    }

    /**
     * A non-null {@link StreamingHandle} for dispatchers that must supply one in their per-callback contexts. Its
     * {@code cancel()} is a no-op; subscription-based providers cancel via {@code Flow.Subscription} instead.
     */
    public StreamingHandle streamingHandle() {
        return streamingHandle;
    }

    /**
     * Aborts the upstream stream if a genuine {@link StreamingHandle} was captured from the per-callback contexts
     * (handle-based providers). Wire this to {@code tube.whenTerminates(...)} so a downstream cancellation (or a
     * buffer overflow) tears down the upstream instead of draining it. Idempotent; a no-op for subscription-based
     * providers, which never supply an upstream handle here.
     */
    public void cancelUpstream() {
        StreamingHandle handle = upstreamHandle.get();
        if (handle != null) {
            handle.cancel();
        }
    }

    private void capture(StreamingHandle handle) {
        // Ignore this bridge's own no-op handle (subscription-based providers put it in the contexts); only a genuine
        // upstream handle from a handle-based provider is worth capturing.
        if (handle == null || handle == streamingHandle) {
            return;
        }
        upstreamHandle.compareAndSet(null, handle);
        if (tube.cancelled()) {
            // Downstream cancelled before we had a handle to abort with (or between capture and the whenTerminates
            // callback): abort now. cancel() is idempotent.
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
