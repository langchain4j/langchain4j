package dev.langchain4j.model.cohere.common;

import dev.langchain4j.model.cohere.CohereEmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.common.AbstractEmbeddingModelIT;
import dev.langchain4j.model.embedding.listener.EmbeddingModelListener;
import java.util.List;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "COHERE_API_KEY", matches = ".+")
class CohereEmbeddingModelIT extends AbstractEmbeddingModelIT {

    private static final String API_KEY = System.getenv("COHERE_API_KEY");
    private static final String MODEL_NAME = "embed-v4.0";

    @Override
    protected List<EmbeddingModel> models() {
        return List.of(CohereEmbeddingModel.builder()
                .apiKey(API_KEY)
                .modelName(MODEL_NAME)
                .build());
    }

    @Override
    protected EmbeddingModel modelWith(EmbeddingModelListener listener) {
        return CohereEmbeddingModel.builder()
                .apiKey(API_KEY)
                .modelName(MODEL_NAME)
                .listeners(List.of(listener))
                .build();
    }

    @Override
    protected EmbeddingModel failingModelWith(EmbeddingModelListener listener) {
        return CohereEmbeddingModel.builder()
                .apiKey("banana")
                .modelName(MODEL_NAME)
                .listeners(List.of(listener))
                .build();
    }

    // Cohere Embed v4 is multimodal (text + image, fused) and maps input_type to search_query/search_document.
    @Override
    protected boolean supportsInputTypeParameter() {
        return true;
    }

    @Override
    protected boolean supportsImageInput() {
        return true;
    }

    @Override
    protected boolean supportsDimensionsParameter() {
        return false;
    }
}
