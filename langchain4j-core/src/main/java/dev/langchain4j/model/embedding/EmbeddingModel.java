package dev.langchain4j.model.embedding;

import static dev.langchain4j.internal.CompletableFutureUtils.propagateCancellation;
import static dev.langchain4j.internal.Exceptions.unwrapCompletionException;
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
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Represents a model that can convert a given text into an embedding (vector representation of the text).
 */
public interface EmbeddingModel {

    /**
     * Embeds the inputs of the given {@link EmbeddingRequest} and returns the resulting embeddings.
     * <p>
     * An {@link EmbeddingRequest} carries one or more inputs to embed, together with optional per-call
     * {@link EmbeddingRequestParameters parameters} such as {@code dimensions} or an
     * {@link dev.langchain4j.model.embedding.request.EmbeddingInputType input type}. A model only honors the
     * parameters listed in {@link #supportedParameters()} and the content types listed in
     * {@link #supportedContentTypes()}; a request that uses anything else is rejected with an
     * {@link UnsupportedFeatureException} rather than being silently ignored.
     *
     * @param request the inputs to embed and the per-call parameters.
     * @return the embeddings and the response metadata.
     * @since 1.18.0
     */
    @Experimental
    default EmbeddingResponse embed(EmbeddingRequest request) {

        EmbeddingRequest finalRequest = validatedRequest(request);

        List<EmbeddingModelListener> listeners = listeners();
        if (isNullOrEmpty(listeners)) {
            return doEmbed(finalRequest);
        }

        List<TextSegment> textSegments = finalRequest.inputs().stream()
                .map(input -> TextSegment.from(input.text()))
                .toList();
        Map<Object, Object> attributes = new java.util.concurrent.ConcurrentHashMap<>();

        EmbeddingModelListenerUtils.onRequest(
                EmbeddingModelRequestContext.builder()
                        .textSegments(textSegments)
                        .embeddingRequest(finalRequest)
                        .embeddingModel(this)
                        .attributes(attributes)
                        .build(),
                listeners);
        try {
            EmbeddingResponse response = doEmbed(finalRequest);
            Response<List<Embedding>> legacyResponse =
                    Response.from(response.embeddings(), response.metadata().tokenUsage());
            EmbeddingModelListenerUtils.onResponse(
                    EmbeddingModelResponseContext.builder()
                            .embeddingRequest(finalRequest)
                            .embeddingResponse(response)
                            .embeddingModel(this)
                            .attributes(attributes)
                            .response(legacyResponse)
                            .textSegments(textSegments)
                            .build(),
                    listeners);
            return response;
        } catch (Exception error) {
            EmbeddingModelListenerUtils.onError(
                    EmbeddingModelErrorContext.builder()
                            .error(error)
                            .textSegments(textSegments)
                            .embeddingRequest(finalRequest)
                            .embeddingModel(this)
                            .attributes(attributes)
                            .build(),
                    listeners);
            throw error;
        }
    }

    /**
     * Non-blocking counterpart of {@link #embed(EmbeddingRequest)}: embeds the request's inputs and completes the
     * returned future with the embeddings, without blocking the calling thread. The request's parameters are merged
     * with {@link #defaultRequestParameters()} and validated exactly as in {@link #embed(EmbeddingRequest)}, but a
     * validation failure completes the future exceptionally rather than being thrown, so callers always observe
     * errors through the future. {@link #listeners()} are notified around the asynchronous call. Cancelling the
     * returned future cancels the in-flight embedding call for providers whose {@link #doEmbedAsync(EmbeddingRequest)}
     * honors cancellation (best-effort).
     *
     * @param request the inputs to embed and the per-call parameters.
     * @return a {@link CompletableFuture} of the embeddings and the response metadata.
     * @since 1.18.0
     */
    @Experimental
    default CompletableFuture<EmbeddingResponse> embedAsync(EmbeddingRequest request) {

        EmbeddingRequest finalRequest;
        try {
            finalRequest = validatedRequest(request);
        } catch (Exception validationError) {
            return CompletableFuture.failedFuture(validationError);
        }

        List<EmbeddingModelListener> listeners = listeners();
        if (isNullOrEmpty(listeners)) {
            return invokeDoEmbedAsync(finalRequest);
        }

        List<TextSegment> textSegments = finalRequest.inputs().stream()
                .map(input -> TextSegment.from(input.text()))
                .toList();
        Map<Object, Object> attributes = new java.util.concurrent.ConcurrentHashMap<>();

        EmbeddingModelListenerUtils.onRequest(
                EmbeddingModelRequestContext.builder()
                        .textSegments(textSegments)
                        .embeddingRequest(finalRequest)
                        .embeddingModel(this)
                        .attributes(attributes)
                        .build(),
                listeners);

        CompletableFuture<EmbeddingResponse> inFlight = invokeDoEmbedAsync(finalRequest);
        CompletableFuture<EmbeddingResponse> result = inFlight.whenComplete((response, error) -> {
            if (error != null) {
                Throwable cause = unwrapCompletionException(error);
                // Cancellation is a caller action, not a model failure: do not report it to listeners as an error
                // (mirrors ChatModel.chatAsync).
                if (!(cause instanceof CancellationException)) {
                    EmbeddingModelListenerUtils.onError(
                            EmbeddingModelErrorContext.builder()
                                    .error(cause)
                                    .textSegments(textSegments)
                                    .embeddingRequest(finalRequest)
                                    .embeddingModel(this)
                                    .attributes(attributes)
                                    .build(),
                            listeners);
                }
            } else {
                Response<List<Embedding>> legacyResponse =
                        Response.from(response.embeddings(), response.metadata().tokenUsage());
                EmbeddingModelListenerUtils.onResponse(
                        EmbeddingModelResponseContext.builder()
                                .embeddingRequest(finalRequest)
                                .embeddingResponse(response)
                                .embeddingModel(this)
                                .attributes(attributes)
                                .response(legacyResponse)
                                .textSegments(textSegments)
                                .build(),
                        listeners);
            }
        });
        // cancelling the caller-facing future cancels the in-flight call (whenComplete does not propagate cancellation)
        propagateCancellation(result, inFlight);
        return result;
    }

    private CompletableFuture<EmbeddingResponse> invokeDoEmbedAsync(EmbeddingRequest finalRequest) {
        // A provider's doEmbedAsync should return a failed future on error, but guard a synchronous throw so the
        // async contract holds regardless.
        try {
            return doEmbedAsync(finalRequest);
        } catch (Exception error) {
            return CompletableFuture.failedFuture(error);
        }
    }

    private EmbeddingRequest validatedRequest(EmbeddingRequest request) {

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

        return EmbeddingRequest.builder()
                .inputs(request.inputs())
                .parameters(finalParameters)
                .build();
    }

    /**
     * The {@link EmbeddingModelListener}s that are notified around {@link #embed(EmbeddingRequest)}.
     * Configure them on the model's builder (via {@code listeners(...)}). Defaults to an empty list.
     *
     * @since 1.18.0
     */
    @Experimental
    default List<EmbeddingModelListener> listeners() {
        return List.of();
    }

    /**
     * The {@link ModelProvider} of this embedding model (for example {@link ModelProvider#OPEN_AI}). It is made
     * available to {@link #listeners() listeners} through the request, response and error contexts.
     * Defaults to {@link ModelProvider#OTHER}.
     *
     * @since 1.18.0
     */
    @Experimental
    default ModelProvider provider() {
        return OTHER;
    }

    /**
     * Performs the embedding for {@link #embed(EmbeddingRequest)}. This is the low-level method a provider
     * overrides to build the actual API call; {@link #embed(EmbeddingRequest)} handles per-call parameter
     * validation and {@link #listeners() listener} dispatch around it.
     * <p>
     * An implementation must override either this method (preferred) or {@link #embedAll(List)} — the two have
     * mutually-recursive defaults, so overriding neither leads to infinite recursion. The default implementation
     * embeds the text of each input via {@link #embedAll(List)}, which keeps implementations that only provide
     * {@link #embedAll(List)} working.
     * <p>
     * When this method is called, the request's parameters have already been merged with
     * {@link #defaultRequestParameters()} and validated against {@link #supportedParameters()} and
     * {@link #supportedContentTypes()}.
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
                        .build())
                .build();
    }

    /**
     * Non-blocking counterpart of {@link #doEmbed(EmbeddingRequest)}, called by {@link #embedAsync(EmbeddingRequest)}
     * after the request's parameters have been merged and validated. A provider backed by remote HTTP I/O overrides
     * this to return a genuinely non-blocking future (no thread is parked).
     * <p>
     * The default throws {@link UnsupportedOperationException}: a model that is not genuinely asynchronous does not
     * pretend to be. It must opt in by overriding this method, so it can choose an execution strategy appropriate to
     * how it works - real async I/O for a network model, a bounded compute pool (or nothing) for a CPU-bound
     * in-process model - rather than being silently offloaded to a (possibly wrong) thread. A model that has not
     * opted in is still usable from the non-blocking RAG path: {@code EmbeddingStoreContentRetriever.retrieveAsync}
     * offloads its blocking {@link #embed(EmbeddingRequest)} for it.
     *
     * @param request the request, with parameters already merged and validated.
     * @return a {@link CompletableFuture} of the response.
     * @since 1.18.0
     */
    @Experimental
    default CompletableFuture<EmbeddingResponse> doEmbedAsync(EmbeddingRequest request) {
        throw new UnsupportedOperationException("doEmbedAsync() is not implemented by " + getClass().getName());
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
     * The per-call {@link EmbeddingParameter parameters} this model honors. A request that populates a parameter
     * outside this set is rejected by {@link #embed(EmbeddingRequest)} with an {@link UnsupportedFeatureException},
     * so a parameter a model cannot apply is never silently ignored. Defaults to an empty set (the model accepts
     * no per-call parameters).
     *
     * @since 1.18.0
     */
    @Experimental
    default Set<EmbeddingParameter<?>> supportedParameters() {
        return Set.of();
    }

    /**
     * The input {@link ContentType content types} this model can embed. A request whose inputs use a content
     * type outside this set is rejected by {@link #embed(EmbeddingRequest)} with an
     * {@link UnsupportedFeatureException}. Defaults to {@code {TEXT}}; a multimodal model overrides this to also
     * accept, for example, {@link ContentType#IMAGE images}.
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
     * Embed the text content of a {@link TextSegment}.
     * <p>
     * This is a convenience method over {@link #embed(EmbeddingRequest)}, so {@link #listeners() listeners} are
     * notified and the model's default per-call parameters are applied here too.
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
        return Response.from(response.embeddings().get(0), response.metadata().tokenUsage());
    }

    /**
     * Embeds the text content of a list of {@link TextSegment}s.
     * <p>
     * This is a convenience method over {@link #embed(EmbeddingRequest)}, so {@link #listeners() listeners} are
     * notified and the model's default per-call parameters are applied here too. A provider implements its
     * embedding logic by overriding {@link #doEmbed(EmbeddingRequest)} (preferred); it may still override this
     * method for batch-specific behavior that the request/response API does not carry (for example applying a
     * document title from {@link TextSegment} metadata).
     *
     * @param textSegments the text segments to embed.
     * @return the embeddings.
     */
    default Response<List<Embedding>> embedAll(List<TextSegment> textSegments) {
        if (isNullOrEmpty(textSegments)) {
            return Response.from(List.of());
        }
        EmbeddingResponse response =
                embed(EmbeddingRequest.builder().textSegments(textSegments).build());
        return Response.from(response.embeddings(), response.metadata().tokenUsage());
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
     * Returns an {@link EmbeddingModel} that wraps this one and notifies the given listener around each embedding
     * call. Listeners can also be configured directly on a model's builder via {@code listeners(...)} (see
     * {@link #listeners()}), which does not require wrapping.
     *
     * @param listener The listener to add.
     * @return An {@link EmbeddingModel} that notifies the given listener.
     * @since 1.11.0
     * @see #listeners()
     */
    @Experimental
    default EmbeddingModel addListener(EmbeddingModelListener listener) {
        return addListeners(listener == null ? null : List.of(listener));
    }

    /**
     * Returns an {@link EmbeddingModel} that wraps this one and notifies the given listeners (in iteration order)
     * around each embedding call. Listeners can also be configured directly on a model's builder via
     * {@code listeners(...)} (see {@link #listeners()}).
     *
     * @param listeners The listeners to add.
     * @return An {@link EmbeddingModel} that notifies the given listeners.
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
