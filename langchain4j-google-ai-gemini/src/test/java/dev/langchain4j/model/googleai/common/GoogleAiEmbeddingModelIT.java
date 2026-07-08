package dev.langchain4j.model.googleai.common;

import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.common.AbstractEmbeddingModelIT;
import dev.langchain4j.model.embedding.listener.EmbeddingModelListener;
import dev.langchain4j.model.googleai.GoogleAiEmbeddingModel;
import java.util.List;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "GOOGLE_AI_GEMINI_API_KEY", matches = ".+")
class GoogleAiEmbeddingModelIT extends AbstractEmbeddingModelIT {

    private static final String API_KEY = System.getenv("GOOGLE_AI_GEMINI_API_KEY");
    private static final String MODEL_NAME = "gemini-embedding-001";

    @Override
    protected List<EmbeddingModel> models() {
        return List.of(GoogleAiEmbeddingModel.builder()
                .apiKey(API_KEY)
                .modelName(MODEL_NAME)
                .build());
    }

    @Override
    protected EmbeddingModel modelWith(EmbeddingModelListener listener) {
        return GoogleAiEmbeddingModel.builder()
                .apiKey(API_KEY)
                .modelName(MODEL_NAME)
                .listeners(List.of(listener))
                .build();
    }

    @Override
    protected EmbeddingModel failingModelWith(EmbeddingModelListener listener) {
        return GoogleAiEmbeddingModel.builder()
                .apiKey("banana")
                .modelName(MODEL_NAME)
                .maxRetries(0)
                .listeners(List.of(listener))
                .build();
    }

    // gemini-embedding-001 maps input_type -> task type (query/document), but is text-only.
    @Override
    protected boolean supportsInputTypeParameter() {
        return true;
    }

    @Override
    protected boolean supportsImageInput() {
        return false;
    }

    @Override
    protected boolean supportsDimensionsParameter() {
        return false;
    }
}
