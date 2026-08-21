package dev.langchain4j.model.embedding;

import static dev.langchain4j.internal.Utils.isNullOrEmpty;

import dev.langchain4j.Internal;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.listener.EmbeddingModelErrorContext;
import dev.langchain4j.model.embedding.listener.EmbeddingModelListener;
import dev.langchain4j.model.embedding.listener.EmbeddingModelRequestContext;
import dev.langchain4j.model.embedding.listener.EmbeddingModelResponseContext;
import dev.langchain4j.model.embedding.request.EmbeddingRequest;
import dev.langchain4j.model.embedding.response.EmbeddingResponse;
import dev.langchain4j.model.embedding.response.EmbeddingResponseMetadata;
import dev.langchain4j.model.output.Response;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Internal
public class EmbeddingModelListenerUtils {

    private static final Logger LOG = LoggerFactory.getLogger(EmbeddingModelListenerUtils.class);

    private EmbeddingModelListenerUtils() {}

    /**
     * Notifies the model's {@link EmbeddingModel#listeners() listeners} around a raw batch embedding
     * {@code operation} that does not itself route through {@link EmbeddingModel#embed(EmbeddingRequest)}.
     * <p>
     * A provider that overrides {@link EmbeddingModel#embedAll(List)} to apply batch-specific behavior the
     * request/response API does not carry (for example a document title from {@link TextSegment} metadata) can
     * wrap the actual API call with this method so that listeners are still notified consistently. Callers must
     * ensure the wrapped {@code operation} does not itself dispatch listeners, otherwise listeners are notified
     * twice.
     *
     * @param model        the embedding model whose listeners are notified.
     * @param textSegments the segments being embedded.
     * @param operation    the raw embedding call.
     * @return the result of {@code operation}.
     */
    public static Response<List<Embedding>> withListeners(
            EmbeddingModel model, List<TextSegment> textSegments, Supplier<Response<List<Embedding>>> operation) {

        List<EmbeddingModelListener> listeners = model.listeners();
        if (isNullOrEmpty(listeners) || isNullOrEmpty(textSegments)) {
            return operation.get();
        }

        EmbeddingRequest request =
                EmbeddingRequest.builder().textSegments(textSegments).build();
        Map<Object, Object> attributes = new ConcurrentHashMap<>();

        onRequest(
                EmbeddingModelRequestContext.builder()
                        .textSegments(textSegments)
                        .embeddingRequest(request)
                        .embeddingModel(model)
                        .attributes(attributes)
                        .build(),
                listeners);
        try {
            Response<List<Embedding>> response = operation.get();
            EmbeddingResponse embeddingResponse = EmbeddingResponse.builder()
                    .embeddings(response.content())
                    .metadata(EmbeddingResponseMetadata.builder()
                            .modelName(model.modelName())
                            .tokenUsage(response.tokenUsage())
                            .build())
                    .build();
            onResponse(
                    EmbeddingModelResponseContext.builder()
                            .embeddingRequest(request)
                            .embeddingResponse(embeddingResponse)
                            .embeddingModel(model)
                            .attributes(attributes)
                            .response(response)
                            .textSegments(textSegments)
                            .build(),
                    listeners);
            return response;
        } catch (Exception error) {
            onError(
                    EmbeddingModelErrorContext.builder()
                            .error(error)
                            .textSegments(textSegments)
                            .embeddingRequest(request)
                            .embeddingModel(model)
                            .attributes(attributes)
                            .build(),
                    listeners);
            throw error;
        }
    }

    /**
     * Single-embedding overload of {@link #withListeners(EmbeddingModel, List, Supplier)}, for providers that
     * override {@link EmbeddingModel#embed(TextSegment)} with a dedicated API call.
     *
     * @param model       the embedding model whose listeners are notified.
     * @param textSegment the segment being embedded.
     * @param operation   the raw embedding call.
     * @return the result of {@code operation}.
     */
    public static Response<Embedding> withListeners(
            EmbeddingModel model, TextSegment textSegment, Supplier<Response<Embedding>> operation) {

        Response<List<Embedding>> response = withListeners(model, List.of(textSegment), () -> {
            Response<Embedding> single = operation.get();
            return Response.from(List.of(single.content()), single.tokenUsage());
        });
        return Response.from(response.content().get(0), response.tokenUsage());
    }

    static void onRequest(EmbeddingModelRequestContext requestContext, List<EmbeddingModelListener> listeners) {
        if (isNullOrEmpty(listeners)) {
            return;
        }
        listeners.forEach(listener -> {
            try {
                listener.onRequest(requestContext);
            } catch (Exception e) {
                LOG.warn(
                        "An exception occurred during the invocation of the embedding model listener. "
                                + "This exception has been ignored.",
                        e);
            }
        });
    }

    static void onResponse(EmbeddingModelResponseContext responseContext, List<EmbeddingModelListener> listeners) {
        if (isNullOrEmpty(listeners)) {
            return;
        }
        listeners.forEach(listener -> {
            try {
                listener.onResponse(responseContext);
            } catch (Exception e) {
                LOG.warn(
                        "An exception occurred during the invocation of the embedding model listener. "
                                + "This exception has been ignored.",
                        e);
            }
        });
    }

    static void onError(EmbeddingModelErrorContext errorContext, List<EmbeddingModelListener> listeners) {
        if (isNullOrEmpty(listeners)) {
            return;
        }
        listeners.forEach(listener -> {
            try {
                listener.onError(errorContext);
            } catch (Exception e) {
                LOG.warn(
                        "An exception occurred during the invocation of the embedding model listener. "
                                + "This exception has been ignored.",
                        e);
            }
        });
    }
}
