package dev.langchain4j.model.embedding;

import static dev.langchain4j.internal.Utils.copy;
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
import dev.langchain4j.model.embedding.request.EmbeddingParameter;
import dev.langchain4j.model.embedding.request.EmbeddingRequest;
import dev.langchain4j.model.embedding.request.EmbeddingRequestParameters;
import dev.langchain4j.model.embedding.response.EmbeddingResponse;
import dev.langchain4j.model.output.Response;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Internal
final class ListeningEmbeddingModel implements EmbeddingModel {

    private final EmbeddingModel delegate;
    private final List<EmbeddingModelListener> listeners;

    ListeningEmbeddingModel(EmbeddingModel delegate, List<EmbeddingModelListener> listeners) {
        this.delegate = ensureNotNull(delegate, "delegate");
        this.listeners = copy(listeners);
    }

    EmbeddingModel withAdditionalListeners(List<EmbeddingModelListener> additionalListeners) {
        if (additionalListeners == null || additionalListeners.isEmpty()) {
            return this;
        }
        List<EmbeddingModelListener> merged = new ArrayList<>(listeners);
        merged.addAll(additionalListeners);
        return new ListeningEmbeddingModel(delegate, merged);
    }

    @Override
    public Response<Embedding> embed(String text) {
        Map<Object, Object> attributes = new ConcurrentHashMap<>();
        List<TextSegment> textSegmentsForContext;
        try {
            textSegmentsForContext = List.of(TextSegment.from(text));
        } catch (Exception ignored) {
            // keep behavior of delegate.embed(text) while still notifying listeners
            textSegmentsForContext = List.of();
        }

        EmbeddingModelRequestContext requestContext = EmbeddingModelRequestContext.builder()
                .textSegments(textSegmentsForContext)
                .embeddingModel(this)
                .attributes(attributes)
                .build();
        onRequest(requestContext, listeners);
        try {
            Response<Embedding> response = delegate.embed(text);

            Response<List<Embedding>> responseForListeners = Response.from(
                    Collections.singletonList(response.content()), response.tokenUsage(), response.finishReason());

            onResponse(
                    EmbeddingModelResponseContext.builder()
                            .response(responseForListeners)
                            .textSegments(textSegmentsForContext)
                            .embeddingModel(this)
                            .attributes(attributes)
                            .build(),
                    listeners);
            return response;
        } catch (Exception error) {
            onError(
                    EmbeddingModelErrorContext.builder()
                            .error(error)
                            .textSegments(textSegmentsForContext)
                            .embeddingModel(this)
                            .attributes(attributes)
                            .build(),
                    listeners);
            throw error;
        }
    }

    @Override
    public Response<Embedding> embed(TextSegment textSegment) {
        Map<Object, Object> attributes = new ConcurrentHashMap<>();
        List<TextSegment> textSegmentsForContext = (textSegment == null) ? List.of() : List.of(textSegment);

        EmbeddingModelRequestContext requestContext = EmbeddingModelRequestContext.builder()
                .textSegments(textSegmentsForContext)
                .embeddingModel(this)
                .attributes(attributes)
                .build();
        onRequest(requestContext, listeners);
        try {
            Response<Embedding> response = delegate.embed(textSegment);

            Response<List<Embedding>> responseForListeners = Response.from(
                    Collections.singletonList(response.content()), response.tokenUsage(), response.finishReason());

            onResponse(
                    EmbeddingModelResponseContext.builder()
                            .response(responseForListeners)
                            .textSegments(textSegmentsForContext)
                            .embeddingModel(this)
                            .attributes(attributes)
                            .build(),
                    listeners);
            return response;
        } catch (Exception error) {
            onError(
                    EmbeddingModelErrorContext.builder()
                            .error(error)
                            .textSegments(textSegmentsForContext)
                            .embeddingModel(this)
                            .attributes(attributes)
                            .build(),
                    listeners);
            throw error;
        }
    }

    @Override
    public Response<List<Embedding>> embedAll(List<TextSegment> textSegments) {
        Map<Object, Object> attributes = new ConcurrentHashMap<>();
        EmbeddingModelRequestContext requestContext = EmbeddingModelRequestContext.builder()
                .textSegments(textSegments)
                .embeddingModel(this)
                .attributes(attributes)
                .build();
        onRequest(requestContext, listeners);
        try {
            Response<List<Embedding>> response = delegate.embedAll(textSegments);
            onResponse(
                    EmbeddingModelResponseContext.builder()
                            .response(response)
                            .textSegments(textSegments)
                            .embeddingModel(this)
                            .attributes(attributes)
                            .build(),
                    listeners);
            return response;
        } catch (Exception error) {
            onError(
                    EmbeddingModelErrorContext.builder()
                            .error(error)
                            .textSegments(textSegments)
                            .embeddingModel(this)
                            .attributes(attributes)
                            .build(),
                    listeners);
            throw error;
        }
    }

    @Override
    public EmbeddingResponse embed(EmbeddingRequest request) {
        Map<Object, Object> attributes = new ConcurrentHashMap<>();
        List<TextSegment> textSegmentsForContext =
                request.inputs().stream().map(input -> TextSegment.from(input.text())).toList();

        EmbeddingModelRequestContext requestContext = EmbeddingModelRequestContext.builder()
                .textSegments(textSegmentsForContext)
                .embeddingModel(this)
                .attributes(attributes)
                .build();
        onRequest(requestContext, listeners);
        try {
            EmbeddingResponse response = delegate.embed(request);

            Response<List<Embedding>> responseForListeners = Response.from(
                    response.embeddings(), response.metadata().tokenUsage());

            onResponse(
                    EmbeddingModelResponseContext.builder()
                            .response(responseForListeners)
                            .textSegments(textSegmentsForContext)
                            .embeddingModel(this)
                            .attributes(attributes)
                            .build(),
                    listeners);
            return response;
        } catch (Exception error) {
            onError(
                    EmbeddingModelErrorContext.builder()
                            .error(error)
                            .textSegments(textSegmentsForContext)
                            .embeddingModel(this)
                            .attributes(attributes)
                            .build(),
                    listeners);
            throw error;
        }
    }

    @Override
    public Set<EmbeddingParameter<?>> supportedParameters() {
        return delegate.supportedParameters();
    }

    @Override
    public EmbeddingRequestParameters defaultRequestParameters() {
        return delegate.defaultRequestParameters();
    }

    @Override
    public String modelName() {
        return delegate.modelName();
    }
}
