package dev.langchain4j.model.jina.common;

import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.common.AbstractEmbeddingModelIT;
import dev.langchain4j.model.embedding.listener.EmbeddingModelListener;
import dev.langchain4j.model.jina.JinaEmbeddingModel;
import java.util.List;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * IT for {@code jina-embeddings-v3}, Jina's text-only task-aware model. It maps the common
 * {@code INPUT_TYPE} parameter onto Jina's {@code task}, so this is where
 * {@link AbstractEmbeddingModelIT#should_embed_query_and_document_differently} actually runs; the sibling
 * {@link JinaEmbeddingModelIT} covers the multimodal, non-task-aware {@code jina-clip-v2}.
 */
@EnabledIfEnvironmentVariable(named = "JINA_API_KEY", matches = ".+")
class JinaV3EmbeddingModelIT extends AbstractEmbeddingModelIT {

    private static final String API_KEY = System.getenv("JINA_API_KEY");
    private static final String MODEL_NAME = "jina-embeddings-v3";

    @Override
    protected List<EmbeddingModel> models() {
        return List.of(JinaEmbeddingModel.builder()
                .apiKey(API_KEY)
                .modelName(MODEL_NAME)
                .build());
    }

    @Override
    protected EmbeddingModel modelWith(EmbeddingModelListener listener) {
        return JinaEmbeddingModel.builder()
                .apiKey(API_KEY)
                .modelName(MODEL_NAME)
                .listeners(List.of(listener))
                .build();
    }

    @Override
    protected EmbeddingModel failingModelWith(EmbeddingModelListener listener) {
        return JinaEmbeddingModel.builder()
                .apiKey("banana")
                .modelName(MODEL_NAME)
                .maxRetries(0)
                .listeners(List.of(listener))
                .build();
    }

    @Override
    protected boolean supportsImageInput() {
        return false; // jina-embeddings-v3 is text-only; jina-clip-v2 and jina-embeddings-v4 are the multimodal ones
    }

    @Override
    protected boolean supportsDimensionsParameter() {
        return false; // Jina's Matryoshka `dimensions` parameter is not mapped by JinaEmbeddingModel yet
    }
}
