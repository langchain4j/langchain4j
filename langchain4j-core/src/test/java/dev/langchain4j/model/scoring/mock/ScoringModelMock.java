package dev.langchain4j.model.scoring.mock;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.Experimental;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.exception.AsyncNotSupportedException;
import dev.langchain4j.internal.AsyncNotSupported;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.model.scoring.ScoringModel;
import dev.langchain4j.model.scoring.listener.ScoringModelListener;
import dev.langchain4j.model.scoring.request.ScoringRequest;
import dev.langchain4j.model.scoring.request.ScoringRequestParameters;
import dev.langchain4j.model.scoring.response.ScoringResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * A {@link ScoringModel} for tests: it scores each document with a fixed or computed score.
 * It supports {@link #score(ScoringRequest)}, {@link #scoreAll(List, String)} (which goes through
 * {@link #score(ScoringRequest)}, so listeners are notified) and {@link #scoreAsync(ScoringRequest)} (the future is
 * already completed when returned), unless created with {@link #withoutAsyncSupport()} or
 * {@link #withAsyncResult(Supplier)}. With {@link #implementingOnlyScoreAll()}, it behaves like a model that implements
 * only {@link #scoreAll(List, String)}.
 * This implementation is experimental and subject to change in the future.
 */
@Experimental
public class ScoringModelMock implements ScoringModel {

    private final Function<String, Double> scores;
    private ModelProvider provider = ModelProvider.OTHER;
    private String modelName;
    private TokenUsage tokenUsage;
    private List<ScoringModelListener> listeners = List.of();
    private boolean asyncSupported = true;
    private Supplier<CompletableFuture<ScoringResponse>> asyncResult;
    private boolean onlyScoreAll;
    private final List<List<TextSegment>> scoreAllCalls = new ArrayList<>();

    /**
     * @param scores computes the score of a document from its text.
     */
    public ScoringModelMock(Function<String, Double> scores) {
        this.scores = ensureNotNull(scores, "scores");
    }

    /**
     * Reports the given provider from {@link #provider()}.
     */
    public ScoringModelMock withProvider(ModelProvider provider) {
        this.provider = provider;
        return this;
    }

    /**
     * Reports the given model name in {@link #defaultRequestParameters()}.
     */
    public ScoringModelMock withModelName(String modelName) {
        this.modelName = modelName;
        return this;
    }

    /**
     * Reports the given token usage in every response.
     */
    public ScoringModelMock withTokenUsage(TokenUsage tokenUsage) {
        this.tokenUsage = tokenUsage;
        return this;
    }

    public ScoringModelMock withListeners(ScoringModelListener... listeners) {
        this.listeners = List.of(listeners);
        return this;
    }

    /**
     * Makes {@link #scoreAsync(ScoringRequest)} fail with an
     * {@link AsyncNotSupportedException}, like a scoring model without asynchronous
     * support.
     */
    public ScoringModelMock withoutAsyncSupport() {
        this.asyncSupported = false;
        return this;
    }

    /**
     * Makes {@link #doScoreAsync(ScoringRequest)} return what the given supplier returns (for example a future that
     * is not completed yet, or {@code null}), or throw what it throws.
     */
    public ScoringModelMock withAsyncResult(Supplier<CompletableFuture<ScoringResponse>> asyncResult) {
        this.asyncResult = asyncResult;
        return this;
    }

    /**
     * Makes this mock behave like a model that implements only {@link #scoreAll(List, String)}: it scores in
     * {@link #scoreAll(List, String)} and keeps the default {@link ScoringModel#doScore(ScoringRequest)}.
     */
    public ScoringModelMock implementingOnlyScoreAll() {
        this.onlyScoreAll = true;
        return this;
    }

    /**
     * @return the segments of each call of {@link #scoreAll(List, String)} made by a mock created with
     * {@link #implementingOnlyScoreAll()}.
     */
    public List<List<TextSegment>> scoreAllCalls() {
        return scoreAllCalls;
    }

    @Override
    public Response<List<Double>> scoreAll(List<TextSegment> segments, String query) {
        if (onlyScoreAll) {
            scoreAllCalls.add(segments);
            return Response.from(segments.stream().map(TextSegment::text).map(scores).toList(), tokenUsage);
        }
        ScoringResponse response = score(ScoringRequest.builder()
                .documents(segments.stream().map(TextSegment::text).toList())
                .query(query)
                .build());
        return Response.from(response.scores(), response.tokenUsage());
    }

    @Override
    public ScoringResponse doScore(ScoringRequest request) {
        if (onlyScoreAll) {
            return ScoringModel.super.doScore(request);
        }
        List<Double> result = request.documents().stream().map(scores).toList();
        return ScoringResponse.builder()
                .scores(result)
                .modelName(request.modelName())
                .tokenUsage(tokenUsage)
                .build();
    }

    @Override
    public CompletableFuture<ScoringResponse> doScoreAsync(ScoringRequest request) {
        if (asyncResult != null) {
            return asyncResult.get();
        }
        if (!asyncSupported) {
            return AsyncNotSupported.failedFuture(getClass(), "doScoreAsync");
        }
        try {
            List<Double> result = request.documents().stream().map(scores).toList();
            return CompletableFuture.completedFuture(ScoringResponse.builder()
                    .scores(result)
                    .modelName(request.modelName())
                    .tokenUsage(tokenUsage)
                    .build());
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    @Override
    public ScoringRequestParameters defaultRequestParameters() {
        return ScoringRequestParameters.builder().modelName(modelName).build();
    }

    @Override
    public List<ScoringModelListener> listeners() {
        return listeners;
    }

    @Override
    public ModelProvider provider() {
        return provider;
    }

    public static ScoringModelMock thatAlwaysScores(double score) {
        return new ScoringModelMock(document -> score);
    }

    public static ScoringModelMock thatScores(Function<String, Double> scores) {
        return new ScoringModelMock(scores);
    }

    public static ScoringModelMock thatAlwaysThrowsException() {
        return thatAlwaysThrowsExceptionWithMessage("Something went wrong, but this is an expected exception");
    }

    public static ScoringModelMock thatAlwaysThrowsExceptionWithMessage(String message) {
        return new ScoringModelMock(document -> {
            throw new RuntimeException(message);
        });
    }
}
