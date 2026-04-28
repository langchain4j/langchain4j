package dev.langchain4j.model.chat.common;

import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;

import java.util.concurrent.Flow;

/**
 * The streaming API a test exercises.
 * Stored on {@link StreamingMetadata} so test bodies can branch on it without resorting to
 * {@code metadata.handler() == null} sentinel checks.
 */
public enum StreamingMode {
    /** Use the handler-based {@link StreamingChatModel#chat(ChatRequest, StreamingChatResponseHandler)} API. */
    HANDLER,
    /** Use the reactive {@link StreamingChatModel#chat(ChatRequest)} API returning a {@link Flow.Publisher}. */
    PUBLISHER
}
