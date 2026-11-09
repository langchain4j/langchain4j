package dev.langchain4j.model.scoring;

import dev.langchain4j.Experimental;
import dev.langchain4j.exception.AsyncNotSupportedException;
import dev.langchain4j.internal.AsyncNotSupported;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.internal.CurrentInvocation;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.scoring.listener.ScoringModelListener;
import dev.langchain4j.model.scoring.request.ScoringRequest;
import dev.langchain4j.model.scoring.request.ScoringRequestParameters;
import dev.langchain4j.model.scoring.response.ScoringResponse;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import static dev.langchain4j.internal.CompletableFutureUtils.propagateCancellation;
import static dev.langchain4j.internal.Exceptions.unwrapCompletionException;
import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.internal.ValidationUtils.ensureEq;
import static dev.langchain4j.model.ModelProvider.OTHER;
import static dev.langchain4j.model.scoring.ScoringModelListenerUtils.onError;
import static dev.langchain4j.model.scoring.ScoringModelListenerUtils.onRequest;
import static dev.langchain4j.model.scoring.ScoringModelListenerUtils.onResponse;
import static java.util.Collections.singletonList;

/**
 * Represents a model capable of scoring a text against a query.
 * <br>
 * Useful for identifying the most relevant texts when scoring multiple texts against the same query.
 * <br>
 * The scoring model can be employed for re-ranking purposes.
 * <p>
 * To support {@link #listeners() listeners}, an implementation overrides {@link #doScore(ScoringRequest)} and
 * implements {@link #scoreAll(List, String)} by calling {@link #score(ScoringRequest)}. It must never make
 * {@link #scoreAll(List, String)} call {@link #score(ScoringRequest)} without overriding
 * {@link #doScore(ScoringRequest)}: the default {@link #doScore(ScoringRequest)} calls
 * {@link #scoreAll(List, String)}, so each method would call the other.
 */
public interface ScoringModel {

    /**
     * Scores a given text against a given query.
     *
     * @param text  The text to be scored.
     * @param query The query against which to score the text.
     * @return the score.
     */
    default Response<Double> score(String text, String query) {
        return score(TextSegment.from(text), query);
    }

    /**
     * Scores a given {@link TextSegment} against a given query.
     *
     * @param segment The {@link TextSegment} to be scored.
     * @param query   The query against which to score the segment.
     * @return the score.
     */
    default Response<Double> score(TextSegment segment, String query) {
        Response<List<Double>> response = scoreAll(singletonList(segment), query);
        ensureEq(response.content().size(), 1,
                "Expected a single score, but received %d", response.content().size());
        return Response.from(response.content().get(0), response.tokenUsage(), response.finishReason());
    }

    /**
     * Scores all provided {@link TextSegment}s against a given query.
     *
     * @param segments The list of {@link TextSegment}s to score.
     * @param query    The query against which to score the segments.
     * @return the list of scores. The order of scores corresponds to the order of {@link TextSegment}s.
     */
    Response<List<Double>> scoreAll(List<TextSegment> segments, String query);

    /**
     * Scores the {@link ScoringRequest#documents() documents} of the given request against its
     * {@link ScoringRequest#query() query}.
     * <p>
     * This applies the model's {@link #defaultRequestParameters() default parameters}, notifies the
     * {@link #listeners() listeners} and dispatches to {@link #doScore(ScoringRequest)}.
     *
     * @param request the documents to score, the query, and the per-call parameters.
     * @return the scores, in the order of {@link ScoringRequest#documents()}, and the response metadata.
     * @since 1.24.0
     */
    @Experimental
    default ScoringResponse score(ScoringRequest request) {
        ScoringRequest finalRequest = withDefaultParameters(request);
        List<ScoringModelListener> listeners = listeners();
        ModelProvider provider = provider();
        Map<Object, Object> attributes = new ConcurrentHashMap<>();
        UUID invocationId = CurrentInvocation.invocationId();

        onRequest(finalRequest, provider, attributes, invocationId, listeners);
        try {
            ScoringResponse response = doScore(finalRequest);
            onResponse(response, finalRequest, provider, attributes, invocationId, listeners);
            return response;
        } catch (Exception error) {
            onError(error, finalRequest, provider, attributes, invocationId, listeners);
            throw error;
        }
    }

    /**
     * The provider hook behind {@link #score(ScoringRequest)}.
     * <p>
     * The default scores the documents with {@link #scoreAll(List, String)}, so a model that implements only
     * {@link #scoreAll(List, String)} also supports {@link #score(ScoringRequest)}. To do so, the default turns each
     * document into a {@link TextSegment}, and a {@link TextSegment} cannot be blank: a model that implements only
     * {@link #scoreAll(List, String)} therefore cannot score blank documents through {@link #score(ScoringRequest)},
     * and such a request fails with an {@link IllegalArgumentException}.
     * <p>
     * To support {@link #listeners() listeners}, override this method and implement {@link #scoreAll(List, String)}
     * by calling {@link #score(ScoringRequest)}, so that the listeners are also notified when
     * {@link #scoreAll(List, String)} is called. Never make {@link #scoreAll(List, String)} call
     * {@link #score(ScoringRequest)} without overriding this method: each method would call the other.
     *
     * @param request the request, with the default parameters already applied.
     * @return the scores, in the order of {@link ScoringRequest#documents()}, and the response metadata.
     * @since 1.24.0
     */
    @Experimental
    default ScoringResponse doScore(ScoringRequest request) {
        List<TextSegment> segments =
                request.documents().stream().map(TextSegment::from).toList();
        Response<List<Double>> response = scoreAll(segments, request.query());
        return ScoringResponse.builder()
                .scores(response.content())
                .modelName(request.modelName())
                .tokenUsage(response.tokenUsage())
                .build();
    }

    /**
     * Scores the {@link ScoringRequest#documents() documents} of the given request against its
     * {@link ScoringRequest#query() query} without blocking the calling thread — the asynchronous/reactive
     * counterpart of {@link #score(ScoringRequest)}, used by the non-blocking RAG flow (see
     * {@code ReRankingContentAggregator}).
     * <p>
     * This applies the model's {@link #defaultRequestParameters() default parameters}, notifies the
     * {@link #listeners() listeners} and dispatches to {@link #doScoreAsync(ScoringRequest)}; a model becomes
     * genuinely non-blocking by overriding the latter. This method never throws: any failure, including an invalid
     * request, completes the returned future exceptionally.
     * <p>
     * When {@link #doScoreAsync(ScoringRequest)} is not implemented, the returned future fails with an
     * {@link AsyncNotSupportedException}, and the listeners are notified of it as an error (listeners that count
     * errors may want to leave it out). A RAG component built with {@code offloadBlocking(true)} then runs the
     * synchronous call (for example {@link #scoreAll(List, String)}) on another thread, which the listeners observe
     * as a separate call; without it, the RAG call fails with an {@code UnsupportedFeatureException}. When the returned future is cancelled, the listeners are notified of neither a response nor an
     * error.
     *
     * @param request the documents to score, the query, and the per-call parameters.
     * @return a {@link CompletableFuture} of the scores, in the order of {@link ScoringRequest#documents()}.
     * @since 1.20.0
     */
    @Experimental
    default CompletableFuture<ScoringResponse> scoreAsync(ScoringRequest request) {
        ScoringRequest finalRequest;
        List<ScoringModelListener> listeners;
        ModelProvider provider;
        Map<Object, Object> attributes;
        UUID invocationId = CurrentInvocation.invocationId();
        try {
            finalRequest = withDefaultParameters(request);
            listeners = listeners();
            provider = provider();
            attributes = new ConcurrentHashMap<>();
            onRequest(finalRequest, provider, attributes, invocationId, listeners);
        } catch (Exception error) {
            return CompletableFuture.failedFuture(error);
        }

        CompletableFuture<ScoringResponse> source;
        try {
            source = doScoreAsync(finalRequest);
            if (source == null) {
                throw new IllegalStateException(getClass().getName() + ".doScoreAsync(ScoringRequest) returned null");
            }
        } catch (Exception error) {
            onError(error, finalRequest, provider, attributes, invocationId, listeners);
            return CompletableFuture.failedFuture(error);
        }
        if (isNullOrEmpty(listeners)) {
            return source;
        }

        CompletableFuture<ScoringResponse> result = source.whenComplete((response, error) -> {
            if (error != null) {
                Throwable cause = unwrapCompletionException(error);
                if (!(cause instanceof CancellationException)) {
                    onError(cause, finalRequest, provider, attributes, invocationId, listeners);
                }
            } else {
                onResponse(response, finalRequest, provider, attributes, invocationId, listeners);
            }
        });
        propagateCancellation(result, source);
        return result;
    }

    /**
     * The provider hook behind {@link #scoreAsync(ScoringRequest)}. The default returns a failed future carrying
     * {@link AsyncNotSupportedException}: a scoring model that is not genuinely asynchronous does not pretend to be.
     * A model backed by remote HTTP I/O opts in by overriding this with a genuinely async call (no thread parked),
     * and should abort its in-flight call when the returned future is cancelled (best-effort). A model that has not
     * opted in fails loudly on the async path rather than silently blocking a carrier thread.
     *
     * @param request the (already defaults-applied) request to score.
     * @return a {@link CompletableFuture} of the scores, in the order of {@link ScoringRequest#documents()}.
     * @since 1.20.0
     */
    @Experimental
    default CompletableFuture<ScoringResponse> doScoreAsync(ScoringRequest request) {
        return AsyncNotSupported.failedFuture(getClass(), "doScoreAsync");
    }

    /**
     * The model's default per-call parameters, applied by {@link #score(ScoringRequest)} and
     * {@link #scoreAsync(ScoringRequest)} to every request and overridden by any parameters set on the request itself. The default is {@link ScoringRequestParameters#EMPTY}.
     *
     * @since 1.20.0
     */
    @Experimental
    default ScoringRequestParameters defaultRequestParameters() {
        return ScoringRequestParameters.EMPTY;
    }

    /**
     * The listeners notified before each call of {@link #score(ScoringRequest)} and
     * {@link #scoreAsync(ScoringRequest)}, after each successful call and when a call fails. Listeners are configured
     * on the builder of the scoring model. Defaults to an empty list.
     *
     * @since 1.24.0
     */
    @Experimental
    default List<ScoringModelListener> listeners() {
        return List.of();
    }

    /**
     * The {@link ModelProvider} of this scoring model (for example {@link ModelProvider#COHERE}). It is made
     * available to the {@link #listeners() listeners}. Defaults to {@link ModelProvider#OTHER}.
     *
     * @since 1.24.0
     */
    @Experimental
    default ModelProvider provider() {
        return OTHER;
    }

    private ScoringRequest withDefaultParameters(ScoringRequest request) {
        return ScoringRequest.builder()
                .documents(request.documents())
                .query(request.query())
                .parameters(defaultRequestParameters().overrideWith(request.parameters()))
                .build();
    }
}
