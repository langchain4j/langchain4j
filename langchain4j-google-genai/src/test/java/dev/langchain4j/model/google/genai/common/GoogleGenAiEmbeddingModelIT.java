package dev.langchain4j.model.google.genai.common;

import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.common.AbstractEmbeddingModelIT;
import dev.langchain4j.model.embedding.listener.EmbeddingModelListener;
import dev.langchain4j.model.google.genai.GoogleGenAiEmbeddingModel;
import java.util.List;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "GOOGLE_AI_GEMINI_API_KEY", matches = ".+")
class GoogleGenAiEmbeddingModelIT extends AbstractEmbeddingModelIT {

    private static final String API_KEY = System.getenv("GOOGLE_AI_GEMINI_API_KEY");
    private static final String MODEL_NAME = "gemini-embedding-001";

    @Override
    protected List<EmbeddingModel> models() {
        return List.of(GoogleGenAiEmbeddingModel.builder()
                .apiKey(API_KEY)
                .modelName(MODEL_NAME)
                .build());
    }

    @Override
    protected EmbeddingModel modelWith(EmbeddingModelListener listener) {
        return GoogleGenAiEmbeddingModel.builder()
                .apiKey(API_KEY)
                .modelName(MODEL_NAME)
                .listeners(List.of(listener))
                .build();
    }

    @Override
    protected EmbeddingModel failingModelWith(EmbeddingModelListener listener) {
        return GoogleGenAiEmbeddingModel.builder()
                .apiKey("banana") // invalid key -> the request fails
                .modelName(MODEL_NAME)
                .maxRetries(0)
                .listeners(List.of(listener))
                .build();
    }

    // gemini-embedding-001 (via the google-genai SDK) maps input type to task_type (query/document) and supports
    // reducing output dimensionality, but is text-only. Token usage is surfaced only when the API returns
    // per-embedding statistics (the Vertex AI path); the API-key path used here does not.
    @Override
    protected boolean supportsImageInput() {
        return false;
    }

    @Override
    protected boolean assertTokenUsage() {
        return false;
    }
}
