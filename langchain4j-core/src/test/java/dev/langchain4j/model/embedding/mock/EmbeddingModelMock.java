package dev.langchain4j.model.embedding.mock;

import dev.langchain4j.Experimental;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.listener.EmbeddingModelListener;
import dev.langchain4j.model.embedding.request.EmbeddingRequest;
import dev.langchain4j.model.embedding.response.EmbeddingResponse;
import dev.langchain4j.test.mock.MockEmbeddingModel;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * An {@link EmbeddingModel} for tests: it returns the same fixed embedding for every input.
 * This implementation is experimental and subject to change in the future.
 *
 * @deprecated use {@link MockEmbeddingModel} (package {@code dev.langchain4j.test.mock}) from the
 * {@code langchain4j-test} module: {@code MockEmbeddingModel.thatEmbeds(1f, 2f, 3f)} returns the same vector as this
 * class, and {@link MockEmbeddingModel#thatEmbeds(Function)} computes the vector from each text. It supports
 * asynchronous calls, which complete on another thread. It accepts the per-call parameters
 * {@code modelName} and {@code inputType}, which this class rejects, and a response reports the model name.
 * {@code withProvider(...)} has no replacement, and {@code withListeners(...)} has the same name.
 * This class will be removed in 1.26.0 at the earliest.
 */
@Experimental
@Deprecated(since = "1.24.0", forRemoval = true)
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
     *
     * @deprecated use {@link MockEmbeddingModel#withListeners(EmbeddingModelListener...)}
     */
    @Deprecated(since = "1.24.0", forRemoval = true)
    public EmbeddingModelMock withListeners(EmbeddingModelListener... listeners) {
        this.listeners = List.of(listeners);
        return this;
    }

    /**
     * Implements {@link #doEmbedAsync(EmbeddingRequest)}: the returned future completes on another thread.
     *
     * @deprecated has no replacement: {@link MockEmbeddingModel} always supports asynchronous calls
     */
    @Deprecated(since = "1.24.0", forRemoval = true)
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
