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
import java.util.function.ToDoubleFunction;
import org.jspecify.annotations.Nullable;

/**
 * A {@link ScoringModel} for tests: it needs no network, scores each document the way the test tells it to, and records
 * the requests it receives.
 * <p>
 * Script the answers step by step, one step per request. One call of {@code score(...)}, {@code scoreAll(...)} or
 * {@code scoreAsync(...)} is one request, also when it scores several documents. For example, a model that gives every
 * document of every request the same score:
 * <pre>{@code
 * MockScoringModel model = MockScoringModel.thatScores(0.8).thenRepeats();
 * }</pre>
 * Every {@code that...} method creates a mock with its first step, and every {@code then...} method adds a step. Each
 * step answers exactly one request. A request after the last step fails with an {@link IllegalStateException} that
 * shows the query of the request, so a test notices when the code calls the model more often than the script expects,
 * unless the code under test handles the error itself. {@code score(...)} throws it, and {@code scoreAsync(...)}
 * returns a future that fails with it. So a mock that is called more than once, for example to re-rank the results of
 * every question of a conversation, ends its script with {@link #thenRepeats()}, which answers every further request
 * with the last step.
 * <p>
 * A script can let a request fail and the next one succeed, for example to test how your application handles a
 * re-ranking model that times out once and then recovers:
 * <pre>{@code
 * MockScoringModel model = MockScoringModel.thatFails(new TimeoutException("Read timed out"))
 *         .thenScores(document -> document.contains("refund") ? 0.9 : 0.1)
 *         .thenRepeats();
 * }</pre>
 * A response reports the model name of the request. {@code scoreAsync(...)} completes on another thread, where a step
 * that computes its scores also computes them.
 * <p>
 * A mock remembers which steps were taken and which requests it received, so create a new one for each test. A mock
 * shared between tests, for example as a bean, fails with a clear message as soon as its script runs out, unless the
 * script ends with {@link #thenRepeats()}. The steps are taken in the order the requests arrive. A mock keeps every
 * request it receives, so it is not meant for load tests or other long runs.
 */
@Experimental
public class MockScoringModel implements ScoringModel {

    private static final String MOCK_NAME = "MockScoringModel";

    /**
     * One step of the script: the score of each document of one request, or the failure of that request.
     *
     * @param scores  computes the score of a document, or {@code null} for a step that fails
     * @param failure the failure of the request, or {@code null} for a step that scores
     */
    record Step(@Nullable ToDoubleFunction<String> scores, @Nullable RuntimeException failure) {}

    private final Script<Step> script = new Script<>(MOCK_NAME);
    private final RecordedRequests<ScoringRequest> requests = new RecordedRequests<>("scoring");
    private List<ScoringModelListener> listeners = List.of();

    MockScoringModel(Step firstStep) {
        script.add(firstStep);
    }

    /**
     * Adds a step: every document of the next request gets the given score.
     *
     * @throws IllegalStateException if the script already ends with {@link #thenRepeats()}
     */
    public MockScoringModel thenScores(double score) {
        return add(fixed(score));
    }

    /**
     * Adds a step: every document of the next request gets the score computed from its text.
     *
     * @throws IllegalStateException if the script already ends with {@link #thenRepeats()}
     */
    public MockScoringModel thenScores(ToDoubleFunction<String> scores) {
        return add(computed(scores));
    }

    /**
     * Adds a step: the next request fails with exactly the given exception, also a request without documents.
     *
     * @throws IllegalStateException if the script already ends with {@link #thenRepeats()}
     */
    public MockScoringModel thenFails(RuntimeException exception) {
        return add(failing(exception));
    }

    /**
     * Answers every further request with the last step of the script. Only the last step repeats; the script does not
     * start over. A last step that computes its scores computes them again for each request. Without it, a request
     * after the last step fails. It must be the last call of the script.
     *
     * @throws IllegalStateException if the script already ends with {@code thenRepeats()}
     */
    public synchronized MockScoringModel thenRepeats() {
        script.repeatLastStep();
        return this;
    }

    private synchronized MockScoringModel add(Step step) {
        script.add(step);
        return this;
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
        return respond(take(request), request);
    }

    /**
     * Answers like {@link #doScore(ScoringRequest)}, with the next step of the script, and completes the future on
     * another thread, where a step that computes its scores also computes them. It never throws: a {@code null} request
     * gives a failed future.
     */
    @Override
    public CompletableFuture<ScoringResponse> doScoreAsync(ScoringRequest request) {
        Step step;
        try {
            step = take(request);
        } catch (RuntimeException e) {
            return CompletableFuture.failedFuture(e);
        }
        return CompletableFuture.supplyAsync(() -> respond(step, request), MockThreads.newThreadPerTask(MOCK_NAME));
    }

    /**
     * Records the request and takes the step that answers it, in one go, so that concurrent requests get consistent
     * request numbers. A request after the last step gets a step that fails with an {@link IllegalStateException}.
     *
     * @throws IllegalArgumentException if the request is {@code null}; it is then not recorded
     */
    private synchronized Step take(ScoringRequest request) {
        ensureNotNull(request, "request");
        int requestNumber = requests.add(request);
        Step step = script.next();
        return step != null ? step : failing(new IllegalStateException(runOutMessage(request, requestNumber)));
    }

    private static ScoringResponse respond(Step step, ScoringRequest request) {
        RuntimeException failure = step.failure();
        if (failure != null) {
            throw failure;
        }
        ToDoubleFunction<String> scores = ensureNotNull(step.scores(), "scores");
        return ScoringResponse.builder()
                .scores(request.documents().stream()
                        .map(scores::applyAsDouble)
                        .toList())
                .modelName(request.modelName())
                .build();
    }

    private String runOutMessage(ScoringRequest request, int requestNumber) {
        int documents = request.documents().size();
        String described = "The query of request " + requestNumber + ": \"" + Script.shortened(request.query())
                + "\", with " + documents + (documents == 1 ? " document." : " documents.");
        return script.runOutMessage(requestNumber, "", described);
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
     * A model that answers the first request with the given score for every document. Add the next steps with
     * {@code then...} methods, or give every document of every request this score:
     * {@code thatScores(0.8).thenRepeats()}.
     */
    public static MockScoringModel thatScores(double score) {
        return new MockScoringModel(fixed(score));
    }

    /**
     * A model that answers the first request with the score computed from the text of each document. Add the next steps
     * with {@code then...} methods, or compute the scores of every request with {@code thenRepeats()}. If the function
     * throws, the request fails with that exception, unchanged.
     */
    public static MockScoringModel thatScores(ToDoubleFunction<String> scores) {
        return new MockScoringModel(computed(scores));
    }

    /**
     * A model whose first request fails with exactly the given exception, also a request without documents. To let
     * every request fail, add {@link #thenRepeats()}. To let later requests succeed, for example to test your own retry
     * logic, add steps with {@code then...} methods.
     */
    public static MockScoringModel thatFails(RuntimeException exception) {
        return new MockScoringModel(failing(exception));
    }

    private static Step fixed(double score) {
        return new Step(document -> score, null);
    }

    private static Step computed(ToDoubleFunction<String> scores) {
        return new Step(ensureNotNull(scores, "scores"), null);
    }

    private static Step failing(RuntimeException exception) {
        return new Step(null, ensureNotNull(exception, "exception"));
    }
}
