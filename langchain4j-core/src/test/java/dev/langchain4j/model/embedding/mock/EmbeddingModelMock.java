package dev.langchain4j.model.embedding.mock;

import dev.langchain4j.Experimental;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.listener.EmbeddingModelListener;
import dev.langchain4j.model.embedding.request.EmbeddingRequest;
import dev.langchain4j.model.embedding.response.EmbeddingResponse;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * An {@link EmbeddingModel} for tests: it returns the same fixed embedding for every input.
 * This implementation is experimental and subject to change in the future.
 */
@Experimental
public class EmbeddingModelMock implements EmbeddingModel {

    private ModelProvider provider = ModelProvider.OTHER;
    private List<EmbeddingModelListener> listeners = List.of();
    private boolean asyncSupported;

    /**
     * Reports the given provider from {@link #provider()}.
     */
    public EmbeddingModelMock withProvider(ModelProvider provider) {
        this.provider = provider;
        return this;
    }

    /**
     * Notifies the given listeners, see {@link #listeners()}.
     */
    public EmbeddingModelMock withListeners(EmbeddingModelListener... listeners) {
        this.listeners = List.of(listeners);
        return this;
    }

    /**
     * Implements {@link #doEmbedAsync(EmbeddingRequest)}: the returned future completes on another thread.
     */
    public EmbeddingModelMock withAsyncSupport() {
        this.asyncSupported = true;
        return this;
    }

    @Override
    public CompletableFuture<EmbeddingResponse> doEmbedAsync(EmbeddingRequest request) {
        if (!asyncSupported) {
            return EmbeddingModel.super.doEmbedAsync(request);
        }
        return CompletableFuture.supplyAsync(() -> doEmbed(request));
    }

    @Override
    public EmbeddingResponse doEmbed(EmbeddingRequest request) {
        List<Embedding> embeddings = request.inputs().stream()
                .map(ignored -> Embedding.from(new float[] {1f, 2f, 3f}))
                .toList();
        return EmbeddingResponse.builder().embeddings(embeddings).build();
    }

    @Override
    public List<EmbeddingModelListener> listeners() {
        return listeners;
    }

    @Override
    public ModelProvider provider() {
        return provider;
    }
}
