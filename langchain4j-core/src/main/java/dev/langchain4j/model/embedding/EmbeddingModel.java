package dev.langchain4j.model.embedding;

import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static java.util.Collections.singletonList;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.internal.ValidationUtils;
import dev.langchain4j.model.embedding.listener.EmbeddingModelListener;
import dev.langchain4j.model.output.Response;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a model that can convert a given text into an embedding (vector representation of the text).
 */
public interface EmbeddingModel {

    /**
     * Wraps this {@link EmbeddingModel} with an observing model that dispatches events to the provided listener.
     *
     * @param listener The listener to add.
     * @return An observing {@link EmbeddingModel} that will dispatch events to the provided listener.
     */
    default EmbeddingModel addListener(EmbeddingModelListener listener) {
        return addListeners(listener == null ? null : List.of(listener));
    }

    /**
     * Wraps this {@link EmbeddingModel} with an observing model that dispatches events to the provided listeners.
     * <p>
     * Listeners are called in the order of iteration.
     *
     * @param listeners The listeners to add.
     * @return An observing {@link EmbeddingModel} that will dispatch events to the provided listeners.
     */
    default EmbeddingModel addListeners(Iterable<EmbeddingModelListener> listeners) {
        if (isNullOrEmpty(listeners)) {
            return this;
        }
        List<EmbeddingModelListener> listenerList = new ArrayList<>();
        listeners.forEach(listenerList::add);
        if (this instanceof ObservingEmbeddingModel observingEmbeddingModel) {
            return observingEmbeddingModel.withAdditionalListeners(listenerList);
        }
        return new ObservingEmbeddingModel(this, listenerList);
    }

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
}
