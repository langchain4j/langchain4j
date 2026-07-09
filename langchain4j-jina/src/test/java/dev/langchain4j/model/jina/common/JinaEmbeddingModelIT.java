package dev.langchain4j.model.jina.common;

import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.common.AbstractEmbeddingModelIT;
import dev.langchain4j.model.embedding.listener.EmbeddingModelListener;
import dev.langchain4j.model.jina.JinaEmbeddingModel;
import java.util.List;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "JINA_API_KEY", matches = ".+")
class JinaEmbeddingModelIT extends AbstractEmbeddingModelIT {

    private static final String API_KEY = System.getenv("JINA_API_KEY");
    private static final String MODEL_NAME = "jina-clip-v2";

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

    // jina-clip-v2 embeds text and images, but one modality per input item (it does not fuse interleaved input),
    // and it exposes neither the common input_type parameter nor a per-call dimensions parameter.
    @Override
    protected boolean supportsInterleavedInput() {
        return false;
    }

    @Override
    protected boolean supportsInputTypeParameter() {
        return false;
    }

    @Override
    protected boolean supportsDimensionsParameter() {
        return false;
    }
}
