package dev.langchain4j.model.vertexai.anthropic.internal.client;

import dev.langchain4j.model.vertexai.anthropic.internal.api.AnthropicResponse;

/**
 * Handler interface for processing streaming responses from Vertex AI Anthropic API.
 */
public interface StreamingResponseHandler {

    /**
     * Called with the complete response metadata (for token usage, etc.).
     * @param response The complete response with metadata
     */
    default void onResponse(AnthropicResponse response) {
        // Default implementation - can be overridden
    }

    /**
     * Called for each chunk of streaming response data.
     * @param jsonChunk The JSON response chunk as a string
     */
    void onChunk(String jsonChunk);

    /**
     * Called when streaming completes successfully.
     */
    void onComplete();

    /**
     * Called when an error occurs during streaming.
     * @param error The error that occurred
     */
    void onError(Throwable error);
}
