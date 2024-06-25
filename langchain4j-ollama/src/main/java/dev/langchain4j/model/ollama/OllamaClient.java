package dev.langchain4j.model.ollama;

public interface OllamaClient {
    ChatResponse chat(ChatRequest request);

    EmbeddingResponse embed(EmbeddingRequest request);
}
