package dev.langchain4j.model.openai.common;

import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.common.AbstractEmbeddingModelIT;
import dev.langchain4j.model.embedding.listener.EmbeddingModelListener;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import java.util.List;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class OpenAiEmbeddingModelIT extends AbstractEmbeddingModelIT {

    @Override
    protected List<EmbeddingModel> models() {
        return List.of(OpenAiEmbeddingModel.builder()
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .modelName("text-embedding-3-small")
                .logRequests(true)
                .logResponses(false) // embeddings are huge in logs
                .build());
    }

    @Override
    protected EmbeddingModel modelWith(EmbeddingModelListener listener) {
        return OpenAiEmbeddingModel.builder()
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .modelName("text-embedding-3-small")
                .listeners(List.of(listener))
                .build();
    }

    @Override
    protected EmbeddingModel failingModelWith(EmbeddingModelListener listener) {
        return OpenAiEmbeddingModel.builder()
                .apiKey("banana") // invalid key -> the request fails
                .modelName("text-embedding-3-small")
                .maxRetries(0)
                .listeners(List.of(listener))
                .build();
    }

    // OpenAI text-embedding-3 supports reducing output dimensionality, but not query/document input
    // types or image inputs.
    @Override
    protected boolean supportsInputTypeParameter() {
        return false;
    }

    @Override
    protected boolean supportsImageInput() {
        return false;
    }
}
