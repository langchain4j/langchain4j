package dev.langchain4j.model.chat;

import dev.langchain4j.Internal;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingEvent;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Bridges a single reactive {@link StreamingChatModel#chat(ChatRequest)} call to a {@link CompletableFuture} of its
 * terminal {@link ChatResponse}: it subscribes with unbounded demand and completes the future with the last
 * {@link ChatResponse} emitted by the stream. This lets callers that need the whole response (driving a tool loop,
 * output-guardrail reprompts, ...) work with a streaming-only model without blocking.
 * <p>
 * Uses the reactive publisher API, not the handler-based {@code chat(request, handler)} overload. This is a
 * <b>raw</b> model call: it does not fire request/response observability events.
 */
@Internal
public final class StreamingChatModelHelper {

    private static final Logger log = LoggerFactory.getLogger(StreamingChatModelHelper.class);

    private StreamingChatModelHelper() {}

    public static CompletableFuture<ChatResponse> chatAsync(StreamingChatModel streamingChatModel, ChatRequest request) {
        return chatAsync(streamingChatModel, request, null);
    }

    /**
     * As {@link #chatAsync(StreamingChatModel, ChatRequest)}, but additionally notifies {@code errorHandler} (if
     * non-null) when the stream fails, before the returned future completes exceptionally.
     */
    public static CompletableFuture<ChatResponse> chatAsync(
            StreamingChatModel streamingChatModel, ChatRequest request, Consumer<Throwable> errorHandler) {
        CompletableFuture<ChatResponse> future = new CompletableFuture<>();
        streamingChatModel.chat(request).subscribe(new Flow.Subscriber<>() {

            private ChatResponse response;

            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(StreamingEvent event) {
                if (event instanceof ChatResponse chatResponse) {
                    this.response = chatResponse;
                }
            }

            @Override
            public void onError(Throwable error) {
                if (errorHandler != null) {
                    try {
                        errorHandler.accept(error);
                    } catch (Exception e) {
                        log.error("While handling the following error...", error);
                        log.error("...the following error happened", e);
                    }
                }
                future.completeExceptionally(error);
            }

            @Override
            public void onComplete() {
                future.complete(response != null ? response : ChatResponse.builder().build());
            }
        });
        return future;
    }
}
