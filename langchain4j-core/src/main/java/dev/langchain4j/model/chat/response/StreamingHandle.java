package dev.langchain4j.model.chat.response;

/**
 * Handle that can be used to cancel the streaming done via {@link StreamingChatResponseHandler}.
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
