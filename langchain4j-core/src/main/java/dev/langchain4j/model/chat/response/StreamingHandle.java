package dev.langchain4j.model.chat.response;

import dev.langchain4j.Experimental;

/**
 * Handle that can be used to cancel the streaming done via {@link StreamingChatResponseHandler}.
 * <p>
 * The handle is passed to the {@link StreamingChatResponseHandler} callbacks that accept a context:
 * {@link StreamingChatResponseHandler#onPartialResponse(PartialResponse, PartialResponseContext)},
 * {@link StreamingChatResponseHandler#onPartialThinking(PartialThinking, PartialThinkingContext)} and
 * {@link StreamingChatResponseHandler#onPartialToolCall(PartialToolCall, PartialToolCallContext)},
 * so it becomes available as soon as the model streams its first partial response
 * (there is nothing to cancel through it before that).
 * You can call {@link #cancel()} right inside the callback,
 * or keep a reference to the handle and call {@link #cancel()} later,
 * for example when the user presses a "Stop" button:
 * <pre>{@code
 * AtomicReference<StreamingHandle> streamingHandle = new AtomicReference<>();
 *
 * model.chat("Tell me a long story", new StreamingChatResponseHandler() {
 *
 *     @Override
 *     public void onPartialResponse(PartialResponse partialResponse, PartialResponseContext context) {
 *         streamingHandle.set(context.streamingHandle());
 *         System.out.print(partialResponse.text());
 *     }
 *
 *     @Override
 *     public void onCompleteResponse(ChatResponse completeResponse) {
 *     }
 *
 *     @Override
 *     public void onError(Throwable error) {
 *         error.printStackTrace();
 *     }
 * });
 *
 * // later, for example when the user presses "Stop":
 * StreamingHandle handle = streamingHandle.get();
 * if (handle != null) { // null until the first partial response arrives
 *     handle.cancel();
 * }
 * }</pre>
 * Depending on the {@link dev.langchain4j.model.chat.StreamingChatModel} implementation,
 * cancelling also closes the connection to the LLM provider, so that it can stop generating further tokens.
 * <p>
 * When using AI Services, the same handle is available in the {@code TokenStream} callbacks
 * whose names end with {@code WithContext}, such as {@code TokenStream.onPartialResponseWithContext(BiConsumer)}.
 *
 * @since 1.8.0
 */
@Experimental
public interface StreamingHandle {

    /**
     * Cancels the streaming.
     * Once this method has been called, the {@link StreamingChatResponseHandler} does not receive
     * any further callbacks, including {@link StreamingChatResponseHandler#onCompleteResponse(ChatResponse)}
     * and {@link StreamingChatResponseHandler#onError(Throwable)}.
     *
     * @throws dev.langchain4j.exception.UnsupportedFeatureException if the
     *         {@link dev.langchain4j.model.chat.StreamingChatModel} implementation does not support cancellation
     */
    void cancel();

    /**
     * Returns {@code true} if streaming was cancelled by calling {@link #cancel()}.
     */
    boolean isCancelled();
}
