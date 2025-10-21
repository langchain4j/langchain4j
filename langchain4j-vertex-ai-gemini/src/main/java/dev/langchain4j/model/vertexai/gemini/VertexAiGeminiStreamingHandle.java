package dev.langchain4j.model.vertexai.gemini;

import dev.langchain4j.model.chat.response.StreamingHandle;

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
