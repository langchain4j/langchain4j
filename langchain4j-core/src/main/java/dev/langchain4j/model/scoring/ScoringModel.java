package dev.langchain4j.model.scoring;

import dev.langchain4j.Experimental;
import dev.langchain4j.exception.AsyncNotSupportedException;
import dev.langchain4j.internal.AsyncNotSupported;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.scoring.request.ScoringRequest;
import dev.langchain4j.model.scoring.request.ScoringRequestParameters;
import dev.langchain4j.model.scoring.response.ScoringResponse;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static dev.langchain4j.internal.ValidationUtils.ensureEq;
import static java.util.Collections.singletonList;

/**
 * Represents a model capable of scoring a text against a query.
 * <br>
 * Useful for identifying the most relevant texts when scoring multiple texts against the same query.
 * <br>
 * The scoring model can be employed for re-ranking purposes.
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
     * {@link ScoringRequest#query() query} without blocking the calling thread — the asynchronous/reactive
     * counterpart of {@link #scoreAll(List, String)}, used by the non-blocking RAG flow (see
     * {@code ReRankingContentAggregator}).
     * <p>
     * This applies the model's {@link #defaultRequestParameters() default parameters} and dispatches to
     * {@link #doScoreAsync(ScoringRequest)}; a model becomes genuinely non-blocking by overriding the latter.
     *
     * @param request the documents to score, the query, and the per-call parameters.
     * @return a {@link CompletableFuture} of the scores, in the order of {@link ScoringRequest#documents()}.
     * @since 1.19.0
     */
    @Experimental
    default CompletableFuture<ScoringResponse> scoreAsync(ScoringRequest request) {
        ScoringRequest finalRequest = ScoringRequest.builder()
                .documents(request.documents())
                .query(request.query())
                .parameters(defaultRequestParameters().overrideWith(request.parameters()))
                .build();
        return doScoreAsync(finalRequest);
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
     * @since 1.19.0
     */
    @Experimental
    default CompletableFuture<ScoringResponse> doScoreAsync(ScoringRequest request) {
        return AsyncNotSupported.failedFuture(getClass(), "doScoreAsync");
    }

    /**
     * The model's default per-call parameters, applied by {@link #scoreAsync(ScoringRequest)} to every request and
     * overridden by any parameters set on the request itself. The default is {@link ScoringRequestParameters#EMPTY}.
     *
     * @since 1.19.0
     */
    @Experimental
    default ScoringRequestParameters defaultRequestParameters() {
        return ScoringRequestParameters.EMPTY;
    }
}
