package dev.langchain4j.model.chat.response;

/**
 * Streaming handle that can be used to cancel the streaming.
 * Used together with {@link StreamingChatResponseHandler}.
 *
 * @since 1.8.0
 */
public interface StreamingHandle {

    /**
     * Cancels the streaming.
     */
    void cancel();

    /**
     * Returns {@code true} if streaming was cancelled by calling {@link #cancel()}.
     */
    boolean isCancelled();
}
