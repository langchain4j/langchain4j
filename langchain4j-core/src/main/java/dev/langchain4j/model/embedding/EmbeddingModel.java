package dev.langchain4j.model.embedding;

import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static java.util.Collections.singletonList;

import dev.langchain4j.Experimental;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.internal.DefaultExecutorProvider;
import dev.langchain4j.internal.ValidationUtils;
import dev.langchain4j.model.embedding.listener.EmbeddingModelListener;
import dev.langchain4j.model.output.Response;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Represents a model that can convert a given text into an embedding (vector representation of the text).
 */
public interface EmbeddingModel {

    /**
     * Embed a text.
     *
     * @param text the text to embed.
     * @return the embedding.
     */
    default Response<Embedding> embed(String text) {
        return embed(TextSegment.from(text));
    }

    /**
     * Embed the text content of a TextSegment.
     *
     * @param textSegment the text segment to embed.
     * @return the embedding.
     */
    default Response<Embedding> embed(TextSegment textSegment) {
        Response<List<Embedding>> response = embedAll(singletonList(textSegment));
        ValidationUtils.ensureEq(
                response.content().size(),
                1,
                "Expected a single embedding, but got %d",
                response.content().size());
        return Response.from(response.content().get(0), response.tokenUsage(), response.finishReason());
    }

    /**
     * Embeds the text content of a list of TextSegments.
     *
     * @param textSegments the text segments to embed.
     * @return the embeddings.
     */
    Response<List<Embedding>> embedAll(List<TextSegment> textSegments);

    /**
     * Non-blocking counterpart of {@link #embedAll(List)}, used by the asynchronous and reactive RAG flow
     * (see {@code EmbeddingStoreContentRetriever.retrieveAsync}).
     * <p>
     * The default implementation offloads the blocking {@link #embedAll(List)} to a shared virtual-thread executor,
     * so any existing {@link EmbeddingModel} is usable from the non-blocking path without changes (blocking a
     * <i>virtual</i> thread is non-pinning). A model backed by remote HTTP I/O is encouraged to override this with a
     * genuinely async call (no thread parked); a purely in-process (CPU-bound) model can leave the default, since a
     * single query embedding is short-lived.
     * <p>
     * An implementation that honors cancellation should abort in-flight I/O when the returned future is cancelled
     * (best-effort).
     *
     * @param textSegments the text segments to embed.
     * @return a {@link CompletableFuture} of the embeddings.
     * @since 1.17.0
     */
    default CompletableFuture<Response<List<Embedding>>> embedAllAsync(List<TextSegment> textSegments) {
        return CompletableFuture.supplyAsync(
                () -> embedAll(textSegments), DefaultExecutorProvider.getDefaultExecutorService());
    }

    /**
     * Returns the dimension of the {@link Embedding} produced by this embedding model.
     *
     * @return dimension of the embedding
     */
    default int dimension() {
        return embed("test").content().dimension();
    }

    /**
     * Returns the name of the underlying embedding model.
     * <p>
     * Implementations are encouraged to override this method and provide the actual model name.
     * The default implementation returns {@code "unknown"}, which indicates
     * that the model name is unknown.
     *
     * @return the model name or a fallback value if not provided
     */
    default String modelName() {
        return "unknown";
    }

    /**
     * Wraps this {@link EmbeddingModel} with a listening model that dispatches events to the provided listener.
     *
     * @param listener The listener to add.
     * @return An observing {@link EmbeddingModel} that will dispatch events to the provided listener.
     * @since 1.11.0
     */
    @Experimental
    default EmbeddingModel addListener(EmbeddingModelListener listener) {
        return addListeners(listener == null ? null : List.of(listener));
    }

    /**
     * Wraps this {@link EmbeddingModel} with a listening model that dispatches events to the provided listeners.
     * <p>
     * Listeners are called in the order of iteration.
     *
     * @param listeners The listeners to add.
     * @return An observing {@link EmbeddingModel} that will dispatch events to the provided listeners.
     * @since 1.11.0
     */
    @Experimental
    default EmbeddingModel addListeners(List<EmbeddingModelListener> listeners) {
        if (isNullOrEmpty(listeners)) {
            return this;
        }
        if (this instanceof ListeningEmbeddingModel listeningEmbeddingModel) {
            return listeningEmbeddingModel.withAdditionalListeners(listeners);
        }
        return new ListeningEmbeddingModel(this, listeners);
    }
}
