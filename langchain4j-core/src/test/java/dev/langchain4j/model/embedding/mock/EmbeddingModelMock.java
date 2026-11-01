package dev.langchain4j.model.embedding.mock;

import dev.langchain4j.Experimental;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.request.EmbeddingRequest;
import dev.langchain4j.model.embedding.response.EmbeddingResponse;
import java.util.List;

/**
 * An {@link EmbeddingModel} for tests: it returns the same fixed embedding for every input.
 * This implementation is experimental and subject to change in the future.
 */
@Experimental
public class EmbeddingModelMock implements EmbeddingModel {

    private ModelProvider provider = ModelProvider.OTHER;

    /**
     * Reports the given provider from {@link #provider()}.
     */
    public EmbeddingModelMock withProvider(ModelProvider provider) {
        this.provider = provider;
        return this;
    }

    @Override
    public EmbeddingResponse doEmbed(EmbeddingRequest request) {
        List<Embedding> embeddings = request.inputs().stream()
                .map(ignored -> Embedding.from(new float[] {1f, 2f, 3f}))
                .toList();
        return EmbeddingResponse.builder().embeddings(embeddings).build();
    }

    @Override
    public ModelProvider provider() {
        return provider;
    }
}
