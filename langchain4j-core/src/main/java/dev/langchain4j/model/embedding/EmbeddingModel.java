package dev.langchain4j.model.embedding;

import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.model.ModelProvider.OTHER;

import dev.langchain4j.Experimental;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.ContentType;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.internal.ValidationUtils;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.embedding.listener.EmbeddingModelErrorContext;
import dev.langchain4j.model.embedding.listener.EmbeddingModelListener;
import dev.langchain4j.model.embedding.listener.EmbeddingModelRequestContext;
import dev.langchain4j.model.embedding.listener.EmbeddingModelResponseContext;
import dev.langchain4j.model.embedding.request.EmbeddingInput;
import dev.langchain4j.model.embedding.request.EmbeddingParameter;
import dev.langchain4j.model.embedding.request.EmbeddingRequest;
import dev.langchain4j.model.embedding.request.EmbeddingRequestParameters;
import dev.langchain4j.model.embedding.response.EmbeddingResponse;
import dev.langchain4j.model.embedding.response.EmbeddingResponseMetadata;
import dev.langchain4j.model.output.Response;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Represents a model that can convert a given text into an embedding (vector representation of the text).
 */
public interface EmbeddingModel {

    /**
     * The primary request/response API, mirroring {@link dev.langchain4j.model.chat.ChatModel#chat}.
     * <p>
     * This method merges the model's {@link #defaultRequestParameters() default parameters} with the
     * request's parameters, validates that every populated parameter is
     * {@link #supportedParameters() supported} by this implementation, and then delegates to
     * {@link #doEmbed(EmbeddingRequest)}.
     * <p>
     * Per-call parameters are strictly opt-in: if the (merged) request populates a parameter that this
     * implementation does not declare as supported, an {@link UnsupportedFeatureException} is thrown instead
     * of the parameter being silently ignored. Implementations that have not overridden
     * {@link #doEmbed(EmbeddingRequest)} support no parameters at all, so a plain request (no parameters)
     * works everywhere, while a request that asks for a parameter such an implementation cannot honor fails
     * fast.
     *
     * @param request the request, containing the texts to embed and the per-call parameters.
     * @return the response, containing the embeddings and the response metadata.
     * @since 1.18.0
     */
    @Experimental
    default EmbeddingResponse embed(EmbeddingRequest request) {

        EmbeddingRequestParameters finalParameters =
                defaultRequestParameters().overrideWith(request.parameters());

        Set<EmbeddingParameter<?>> unsupported = new LinkedHashSet<>(finalParameters.presentParameters());
        unsupported.removeAll(supportedParameters());
        if (!unsupported.isEmpty()) {
            String names = unsupported.stream().map(EmbeddingParameter::name).collect(Collectors.joining(", "));
            throw new UnsupportedFeatureException("EmbeddingModel '" + getClass().getName()
                    + "' does not support the following per-call parameter(s): " + names
                    + ". Only the following are supported: "
                    + supportedParameters().stream()
                            .map(EmbeddingParameter::name)
                            .collect(Collectors.joining(", ")));
        }

        Set<ContentType> unsupportedContentTypes = new LinkedHashSet<>();
        for (EmbeddingInput input : request.inputs()) {
            unsupportedContentTypes.addAll(input.contentTypes());
        }
        unsupportedContentTypes.removeAll(supportedContentTypes());
        if (!unsupportedContentTypes.isEmpty()) {
            throw new UnsupportedFeatureException("EmbeddingModel '" + getClass().getName()
                    + "' does not support the following content type(s): " + unsupportedContentTypes
                    + ". Only the following are supported: " + supportedContentTypes());
        }

        EmbeddingRequest finalRequest = EmbeddingRequest.builder()
                .inputs(request.inputs())
                .parameters(finalParameters)
                .build();

        List<EmbeddingModelListener> listeners = listeners();
        if (isNullOrEmpty(listeners)) {
            return doEmbed(finalRequest);
        }

        // Fire listeners inline, mirroring ChatModel. The listener contexts are keyed on the (legacy)
        // List<TextSegment> / Response<List<Embedding>> shape, so we adapt the request/response to it
        // for backward compatibility with existing EmbeddingModelListener implementations.
        List<TextSegment> textSegments = finalRequest.inputs().stream()
                .map(input -> TextSegment.from(input.text()))
                .toList();
        Map<Object, Object> attributes = new java.util.concurrent.ConcurrentHashMap<>();

        ModelProvider provider = provider();
        EmbeddingModelListenerUtils.onRequest(
                EmbeddingModelRequestContext.builder()
                        .textSegments(textSegments)
                        .embeddingModel(this)
                        .modelProvider(provider)
                        .attributes(attributes)
                        .build(),
                listeners);
        try {
            EmbeddingResponse response = doEmbed(finalRequest);
            Response<List<Embedding>> legacyResponse =
                    Response.from(response.embeddings(), response.metadata().tokenUsage());
            EmbeddingModelListenerUtils.onResponse(
                    EmbeddingModelResponseContext.builder()
                            .response(legacyResponse)
                            .textSegments(textSegments)
                            .embeddingModel(this)
                            .modelProvider(provider)
                            .attributes(attributes)
                            .build(),
                    listeners);
            return response;
        } catch (Exception error) {
            EmbeddingModelListenerUtils.onError(
                    EmbeddingModelErrorContext.builder()
                            .error(error)
                            .textSegments(textSegments)
                            .embeddingModel(this)
                            .modelProvider(provider)
                            .attributes(attributes)
                            .build(),
                    listeners);
            throw error;
        }
    }

    /**
     * The {@link EmbeddingModelListener}s to notify around {@link #embed(EmbeddingRequest)}, mirroring
     * {@link dev.langchain4j.model.chat.ChatModel#listeners()}. Defaults to an empty list; a provider overrides
     * it to fire its configured listeners inline (instead of, or in addition to, the wrapper-based
     * {@link #addListener(EmbeddingModelListener)} approach).
     *
     * @since 1.18.0
     */
    @Experimental
    default List<EmbeddingModelListener> listeners() {
        return List.of();
    }

    /**
     * The {@link ModelProvider} of this embedding model, exposed to {@link #listeners() listeners} via the
     * request/response/error contexts (mirroring {@link dev.langchain4j.model.chat.ChatModel#provider()}).
     * Defaults to {@link ModelProvider#OTHER}; providers override it.
     *
     * @since 1.18.0
     */
    @Experimental
    default ModelProvider provider() {
        return OTHER;
    }

    /**
     * The method a provider overrides to natively honor per-call parameters. The default implementation
     * bridges <i>down</i> to the legacy {@link #embedAll(List)}, so every existing implementation supports
     * the new request/response API unchanged. Because this default only calls the (still abstract)
     * {@code embedAll}, there is no default-to-default cycle.
     * <p>
     * The default does not apply any request parameters — it does not need to, because
     * {@link #embed(EmbeddingRequest)} has already rejected any parameter that {@link #supportedParameters()}
     * (empty by default) does not cover.
     *
     * @param request the request, with parameters already merged and validated.
     * @return the response.
     * @since 1.18.0
     */
    @Experimental
    default EmbeddingResponse doEmbed(EmbeddingRequest request) {
        Response<List<Embedding>> legacy = embedAll(
                request.inputs().stream().map(input -> TextSegment.from(input.text())).toList());
        return EmbeddingResponse.builder()
                .embeddings(legacy.content())
                .metadata(EmbeddingResponseMetadata.builder()
                        .modelName(modelName())
                        .tokenUsage(legacy.tokenUsage())
                        .finishReason(legacy.finishReason())
                        .build())
                .build();
    }

    /**
     * The parameters applied to every request unless overridden by the request itself, typically derived from
     * the model's builder-time configuration. The default is {@link EmbeddingRequestParameters#EMPTY}.
     *
     * @since 1.18.0
     */
    @Experimental
    default EmbeddingRequestParameters defaultRequestParameters() {
        return EmbeddingRequestParameters.EMPTY;
    }

    /**
     * The set of per-call parameters this implementation honors. Defaults to the empty set (default-deny):
     * an implementation must explicitly opt into each parameter it supports, otherwise
     * {@link #embed(EmbeddingRequest)} rejects requests that populate it. This guarantees that a newly
     * introduced parameter is never silently ignored by an implementation that has not been updated.
     *
     * @since 1.18.0
     */
    @Experimental
    default Set<EmbeddingParameter<?>> supportedParameters() {
        return Set.of();
    }

    /**
     * The set of input {@link ContentType}s this implementation can embed. Defaults to {@code {TEXT}}: an
     * implementation must explicitly opt into non-text modalities (image/audio/video/pdf), otherwise
     * {@link #embed(EmbeddingRequest)} rejects any input that uses them. Like {@link #supportedParameters()},
     * this is default-deny, so a text-only model never silently ignores (or mis-embeds) an image.
     *
     * @since 1.18.0
     */
    @Experimental
    default Set<ContentType> supportedContentTypes() {
        return Set.of(ContentType.TEXT);
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
     * <p>
     * This convenience method routes through {@link #embed(EmbeddingRequest)}, so per-call parameter/content
     * validation and {@link #listeners() listeners} apply here too. The result is bridged back to the legacy
     * {@link Response} type (including {@link EmbeddingResponseMetadata#finishReason() finishReason}) so the
     * behavior is unchanged for existing callers and implementations.
     *
     * @param textSegment the text segment to embed.
     * @return the embedding.
     */
    default Response<Embedding> embed(TextSegment textSegment) {
        EmbeddingResponse response = embed(EmbeddingRequest.builder()
                .textSegment(textSegment)
                .build());
        ValidationUtils.ensureEq(
                response.embeddings().size(),
                1,
                "Expected a single embedding, but got %d",
                response.embeddings().size());
        return Response.from(
                response.embeddings().get(0),
                response.metadata().tokenUsage(),
                response.metadata().finishReason());
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

    /**
     * Wraps this {@link EmbeddingModel} with a listening model that dispatches events to the provided listener.
     * <p>
     * <b>Preferred approach:</b> where the model's builder exposes a {@code listeners(...)} method, configure
     * listeners there instead. That fires them inline via {@link #listeners()} (mirroring
     * {@link dev.langchain4j.model.chat.ChatModel}) without wrapping. This wrapper-based method remains useful
     * for adding a listener to an already-built model, or for models whose builder does not yet expose
     * {@code listeners(...)}.
     *
     * @param listener The listener to add.
     * @return An observing {@link EmbeddingModel} that will dispatch events to the provided listener.
     * @since 1.11.0
     * @see #listeners()
     */
    @Experimental
    default EmbeddingModel addListener(EmbeddingModelListener listener) {
        return addListeners(listener == null ? null : List.of(listener));
    }

    /**
     * Wraps this {@link EmbeddingModel} with a listening model that dispatches events to the provided listeners.
     * <p>
     * Listeners are called in the order of iteration.
     * <p>
     * <b>Preferred approach:</b> where the model's builder exposes a {@code listeners(...)} method, configure
     * listeners there instead (see {@link #listeners()}); this wrapper remains useful for adding listeners to an
     * already-built model or to models whose builder does not yet expose {@code listeners(...)}.
     *
     * @param listeners The listeners to add.
     * @return An observing {@link EmbeddingModel} that will dispatch events to the provided listeners.
     * @since 1.11.0
     * @see #listeners()
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
