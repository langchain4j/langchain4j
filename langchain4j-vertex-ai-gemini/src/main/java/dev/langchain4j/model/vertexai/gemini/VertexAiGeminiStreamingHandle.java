package dev.langchain4j.model.vertexai.gemini;

import dev.langchain4j.model.chat.response.StreamingHandle;

/**
 * @since 1.8.0
 */
class VertexAiGeminiStreamingHandle implements StreamingHandle {

    private volatile boolean isCancelled;

    @Override
    public void cancel() {
        isCancelled = true;
    }

    @Override
    public boolean isCancelled() {
        return isCancelled;
    }
}
