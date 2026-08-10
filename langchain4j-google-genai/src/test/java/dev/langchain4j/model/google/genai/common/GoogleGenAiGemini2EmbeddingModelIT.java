package dev.langchain4j.model.google.genai.common;

import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.common.AbstractEmbeddingModelIT;
import dev.langchain4j.model.embedding.listener.EmbeddingModelListener;
import dev.langchain4j.model.google.genai.GoogleGenAiEmbeddingModel;
import java.util.List;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * IT for Gemini Embedding 2 via the google-genai SDK. Unlike {@code gemini-embedding-001}, this model does not
 * accept the task type parameter, so the input type is applied as a prompt instruction (see
 * {@link GoogleGenAiEmbeddingModel}).
 */
@EnabledIfEnvironmentVariable(named = "GOOGLE_AI_GEMINI_API_KEY", matches = ".+")
class GoogleGenAiGemini2EmbeddingModelIT extends AbstractEmbeddingModelIT {

    private static final String API_KEY = System.getenv("GOOGLE_AI_GEMINI_API_KEY");
    private static final String MODEL_NAME = "gemini-embedding-2";

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

    // Gemini Embedding 2 is natively multimodal (text + image, fused into a single embedding) and applies the
    // input type as a prompt instruction. It supports reducing output dimensionality, but returns no token usage
    // on the API-key path.
    @Override
    protected boolean assertTokenUsage() {
        return false;
    }
}
