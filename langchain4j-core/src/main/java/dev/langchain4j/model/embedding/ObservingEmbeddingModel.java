package dev.langchain4j.model.embedding;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static dev.langchain4j.model.embedding.EmbeddingModelListenerUtils.onError;
import static dev.langchain4j.model.embedding.EmbeddingModelListenerUtils.onRequest;
import static dev.langchain4j.model.embedding.EmbeddingModelListenerUtils.onResponse;

import dev.langchain4j.Internal;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.listener.EmbeddingModelErrorContext;
import dev.langchain4j.model.embedding.listener.EmbeddingModelListener;
import dev.langchain4j.model.embedding.listener.EmbeddingModelRequestContext;
import dev.langchain4j.model.embedding.listener.EmbeddingModelResponseContext;
import dev.langchain4j.model.output.Response;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Internal
final class ObservingEmbeddingModel implements EmbeddingModel {

    private final EmbeddingModel delegate;
    private final List<EmbeddingModelListener> listeners;

    ObservingEmbeddingModel(EmbeddingModel delegate, List<EmbeddingModelListener> listeners) {
        this.delegate = ensureNotNull(delegate, "delegate");
        this.listeners = ensureNotNull(listeners, "listeners");
    }

    EmbeddingModel withAdditionalListeners(List<EmbeddingModelListener> additionalListeners) {
        if (additionalListeners == null || additionalListeners.isEmpty()) {
            return this;
        }
        List<EmbeddingModelListener> merged = new ArrayList<>(listeners);
        merged.addAll(additionalListeners);
        return new ObservingEmbeddingModel(delegate, merged);
    }

    @Override
    public Response<List<Embedding>> embedAll(List<TextSegment> textSegments) {
        Map<Object, Object> attributes = new ConcurrentHashMap<>();
        EmbeddingModelRequestContext requestContext = new EmbeddingModelRequestContext(textSegments, this, attributes);
        onRequest(requestContext, listeners);
        try {
            Response<List<Embedding>> response = delegate.embedAll(textSegments);
            onResponse(new EmbeddingModelResponseContext(response, textSegments, this, attributes), listeners);
            return response;
        } catch (Exception error) {
            onError(new EmbeddingModelErrorContext(error, textSegments, this, attributes), listeners);
            throw error;
        }
    }

    @Override
    public String modelName() {
        return delegate.modelName();
    }
}



