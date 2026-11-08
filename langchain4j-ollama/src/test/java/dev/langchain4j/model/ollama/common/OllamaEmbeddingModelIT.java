package dev.langchain4j.model.ollama.common;

import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.model.ollama.AbstractOllamaLanguageModelInfrastructure.OLLAMA_BASE_URL;
import static dev.langchain4j.model.ollama.AbstractOllamaLanguageModelInfrastructure.ollamaBaseUrl;
import static dev.langchain4j.model.ollama.OllamaImage.ALL_MINILM_MODEL;
import static dev.langchain4j.model.ollama.OllamaImage.OLLAMA_IMAGE;
import static dev.langchain4j.model.ollama.OllamaImage.localOllamaImage;
import static dev.langchain4j.model.ollama.OllamaImage.resolve;

import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.common.AbstractEmbeddingModelIT;
import dev.langchain4j.model.embedding.listener.EmbeddingModelListener;
import dev.langchain4j.model.ollama.LC4jOllamaContainer;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
import java.util.List;

class OllamaEmbeddingModelIT extends AbstractEmbeddingModelIT {

    private static final String MODEL_NAME = ALL_MINILM_MODEL;

    private static LC4jOllamaContainer ollama;

    static {
        if (isNullOrEmpty(OLLAMA_BASE_URL)) {
            String localOllamaImage = localOllamaImage(MODEL_NAME);
            ollama = new LC4jOllamaContainer(resolve(OLLAMA_IMAGE, localOllamaImage)).withModel(MODEL_NAME);
            ollama.start();
            ollama.commitToImage(localOllamaImage);
        }
    }

    @Override
    protected List<EmbeddingModel> models() {
        return List.of(OllamaEmbeddingModel.builder()
                .baseUrl(ollamaBaseUrl(ollama))
                .modelName(MODEL_NAME)
                .build());
    }

    @Override
    protected EmbeddingModel modelWith(EmbeddingModelListener listener) {
        return OllamaEmbeddingModel.builder()
                .baseUrl(ollamaBaseUrl(ollama))
                .modelName(MODEL_NAME)
                .listeners(List.of(listener))
                .build();
    }

    @Override
    protected EmbeddingModel failingModelWith(EmbeddingModelListener listener) {
        return OllamaEmbeddingModel.builder()
                .baseUrl(ollamaBaseUrl(ollama))
                .modelName("nonexistent-model") // the model is not present -> the request fails
                .maxRetries(0)
                .listeners(List.of(listener))
                .build();
    }

    // Ollama's embedding API is text-only and has no input types. Its optional per-call "dimensions" is
    // model-dependent (the default `all-minilm` model does not support reducing output size), so it is left as a
    // builder-level passthrough rather than an advertised per-call parameter. Token usage (prompt_eval_count) is
    // reported.
    @Override
    protected boolean supportsInputTypeParameter() {
        return false;
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
