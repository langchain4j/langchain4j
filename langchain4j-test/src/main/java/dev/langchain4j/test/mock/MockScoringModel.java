package dev.langchain4j.test.mock;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.Experimental;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.scoring.ScoringModel;
import dev.langchain4j.model.scoring.listener.ScoringModelListener;
import dev.langchain4j.model.scoring.request.ScoringRequest;
import dev.langchain4j.model.scoring.response.ScoringResponse;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;

/**
 * A {@link ScoringModel} for tests: it needs no network, scores each document with a fixed or computed score, and
 * records the requests it receives. It supports {@link #score(ScoringRequest)}, {@link #scoreAll(List, String)} and
 * {@link #scoreAsync(ScoringRequest)}, which completes on another thread. A response reports the model name of the
 * request.
 */
@Experimental
public class MockScoringModel implements ScoringModel {

    private final Function<ScoringRequest, List<Double>> scores;
    private final RecordedRequests<ScoringRequest> requests = new RecordedRequests<>("scoring");
    private List<ScoringModelListener> listeners = List.of();

    MockScoringModel(Function<ScoringRequest, List<Double>> scores) {
        this.scores = ensureNotNull(scores, "scores");
    }

    /**
     * Notifies the given listeners of each call, like a real model does.
     */
    public MockScoringModel withListeners(ScoringModelListener... listeners) {
        this.listeners = List.of(listeners);
        return this;
    }

    /**
     * Notifies the given listeners of each call, like a real model does.
     */
    public MockScoringModel withListeners(List<ScoringModelListener> listeners) {
        this.listeners = List.copyOf(listeners);
        return this;
    }

    @Override
    public Response<List<Double>> scoreAll(List<TextSegment> segments, String query) {
        ScoringResponse response = score(ScoringRequest.builder()
                .documents(segments.stream().map(TextSegment::text).toList())
                .query(query)
                .build());
        return Response.from(response.scores(), response.tokenUsage());
    }

    @Override
    public ScoringResponse doScore(ScoringRequest request) {
        requests.add(request);
        return respond(request);
    }

    @Override
    public CompletableFuture<ScoringResponse> doScoreAsync(ScoringRequest request) {
        requests.add(request);
        return CompletableFuture.supplyAsync(
                () -> respond(request), MockThreads.newThreadPerTask("MockScoringModel"));
    }

    private ScoringResponse respond(ScoringRequest request) {
        return ScoringResponse.builder()
                .scores(scores.apply(request))
                .modelName(request.modelName())
                .build();
    }

    @Override
    public List<ScoringModelListener> listeners() {
        return listeners;
    }

    /**
     * A copy of the requests received so far, in the order they arrived.
     */
    public List<ScoringRequest> requests() {
        return requests.all();
    }

    /**
     * The only request received so far.
     *
     * @throws IllegalStateException if the mock received no request or several requests
     */
    public ScoringRequest request() {
        return requests.only();
    }

    /**
     * The last request received so far.
     *
     * @throws IllegalStateException if the mock received no request
     */
    public ScoringRequest lastRequest() {
        return requests.last();
    }

    /**
     * A model that gives every document the given score.
     */
    public static MockScoringModel thatScores(double score) {
        return thatScores(document -> score);
    }

    /**
     * A model that computes the score of a document from its text.
     */
    public static MockScoringModel thatScores(ToDoubleFunction<String> scores) {
        ensureNotNull(scores, "scores");
        return new MockScoringModel(request -> request.documents().stream()
                .map(scores::applyAsDouble)
                .toList());
    }

    /**
     * A model whose requests all fail with the given exception, also a request without documents.
     */
    public static MockScoringModel thatFails(RuntimeException exception) {
        ensureNotNull(exception, "exception");
        return new MockScoringModel(request -> {
            throw exception;
        });
    }
}
