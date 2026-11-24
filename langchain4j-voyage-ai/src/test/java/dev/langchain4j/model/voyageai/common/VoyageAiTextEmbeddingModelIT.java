package dev.langchain4j.model.voyageai.common;

import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.common.AbstractEmbeddingModelIT;
import dev.langchain4j.model.embedding.listener.EmbeddingModelListener;
import dev.langchain4j.model.voyageai.VoyageAiEmbeddingModel;
import java.util.List;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * IT for {@code voyage-3}, a text-only model served on the text embeddings endpoint. The sibling
 * {@link VoyageAiEmbeddingModelIT} covers the multimodal {@code voyage-multimodal-3.5}, which is served on
 * the multimodal endpoint.
 */
@EnabledIfEnvironmentVariable(named = "VOYAGE_API_KEY", matches = ".+")
class VoyageAiTextEmbeddingModelIT extends AbstractEmbeddingModelIT {

    private static final String API_KEY = System.getenv("VOYAGE_API_KEY");
    private static final String MODEL_NAME = "voyage-3";

    @Override
    protected List<EmbeddingModel> models() {
        return List.of(VoyageAiEmbeddingModel.builder()
                .apiKey(API_KEY)
                .modelName(MODEL_NAME)
                .build());
    }

    @Override
    protected EmbeddingModel modelWith(EmbeddingModelListener listener) {
        return VoyageAiEmbeddingModel.builder()
                .apiKey(API_KEY)
                .modelName(MODEL_NAME)
                .listeners(List.of(listener))
                .build();
    }

    @Override
    protected EmbeddingModel failingModelWith(EmbeddingModelListener listener) {
        return VoyageAiEmbeddingModel.builder()
                .apiKey("banana")
                .modelName(MODEL_NAME)
                .maxRetries(0)
                .listeners(List.of(listener))
                .build();
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
