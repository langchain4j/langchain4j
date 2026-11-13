package dev.langchain4j.test.mock;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.Experimental;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.listener.EmbeddingModelListener;
import dev.langchain4j.model.embedding.request.EmbeddingParameter;
import dev.langchain4j.model.embedding.request.EmbeddingRequest;
import dev.langchain4j.model.embedding.request.EmbeddingRequestParameters;
import dev.langchain4j.model.embedding.response.EmbeddingResponse;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/**
 * An {@link EmbeddingModel} for tests: it needs no network, returns the vectors the test tells it to, and records the
 * requests it receives.
 * <p>
 * Script the answers step by step, one step per request. One call of {@code embed(...)}, {@code embedAll(...)} or
 * {@code embedAsync(...)} is one request, also when it embeds several texts. For example, a model that returns the same
 * vector for every text of every request:
 * <pre>{@code
 * MockEmbeddingModel model = MockEmbeddingModel.thatEmbeds(0.1f, 0.2f).thenRepeats();
 * }</pre>
 * Every {@code that...} method creates a mock with its first step, and every {@code then...} method adds a step. Each
 * step answers exactly one request. A request after the last step fails with an {@link IllegalStateException} that
 * shows the texts of the request, so a test notices when the code calls the model more often than the script expects,
 * unless the code under test handles the error itself. {@code embed(...)} throws it, and {@code embedAsync(...)}
 * returns a future that fails with it. So a mock that is called more than once, for example to embed the documents of
 * a search and later its query, ends its script with {@link #thenRepeats()}, which answers every further request with
 * the last step.
 * <p>
 * A script can let a request fail and the next one succeed, for example to test your own retry logic after a rate
 * limit:
 * <pre>{@code
 * MockEmbeddingModel model = MockEmbeddingModel.thatFails(new RateLimitException("Too many requests"))
 *         .thenEmbeds(0.1f, 0.2f)
 *         .thenRepeats();
 * }</pre>
 * or let the second batch of an ingestion fail and the next batches succeed:
 * <pre>{@code
 * MockEmbeddingModel model = MockEmbeddingModel.thatEmbeds(0.1f, 0.2f)
 *         .thenFails(new InternalServerException("Service unavailable"))
 *         .thenEmbeds(0.1f, 0.2f)
 *         .thenRepeats();
 * }</pre>
 * <p>
 * Use {@link #thatEmbeds(Function)} when the test depends on which texts are close to each other, for example on the
 * ranking of retrieved segments or on a minimum score: the function in the test then says which texts are close. Use
 * {@link #thatEmbeds(float...)} when it does not matter. When a test depends on the meaning of texts, use a real
 * embedding model that runs in-process, for example {@code AllMiniLmL6V2QuantizedEmbeddingModel} from the module
 * {@code langchain4j-embeddings-all-minilm-l6-v2-q}.
 * <p>
 * Like on every mock in this package, {@code thatResponds(...)} and {@code thenResponds(...)} give a step as the whole
 * response, here an {@link EmbeddingResponse}: the request is answered with exactly that response, including its
 * metadata, for example the token usage of an embedding call in a cost test.
 * <p>
 * The mock accepts the per-call parameters {@code modelName} and {@code inputType}, and rejects {@code dimensions} with
 * an {@code UnsupportedFeatureException}, like a model without that parameter; a rejected request takes no step. Unless
 * a step gives the whole response, a response reports the model name of the request. {@code embedAsync(...)} completes on another thread, where a step
 * that computes its vectors also computes them.
 * <p>
 * A mock remembers which steps were taken and which requests it received, so create a new one for each test. A mock
 * shared between tests, for example as a bean, fails with a clear message as soon as its script runs out, unless the
 * script ends with {@link #thenRepeats()}. The steps are taken in the order the requests arrive. A mock keeps every
 * request it receives, so it is not meant for load tests or other long runs.
 */
@Experimental
public class MockEmbeddingModel implements EmbeddingModel {

    private static final String MOCK_NAME = "MockEmbeddingModel";
    private static final int MAX_TEXTS_SHOWN = 3;

    /**
     * One step of the script: the vector of each text of one request, the whole response to that request, or its
     * failure. Exactly one of the three is not {@code null}.
     *
     * @param embeddings computes the vector of a text, or {@code null}
     * @param response   the whole response to the request, or {@code null}
     * @param failure    the failure of the request, or {@code null}
     */
    record Step(
            @Nullable Function<String, float[]> embeddings,
            @Nullable EmbeddingResponse response,
            @Nullable RuntimeException failure) {}

    private final Script<Step> script = new Script<>(MOCK_NAME);
    private final RecordedRequests<EmbeddingRequest> requests = new RecordedRequests<>("embedding");
    private List<EmbeddingModelListener> listeners = List.of();

    MockEmbeddingModel(Step firstStep) {
        script.add(firstStep);
    }

    /**
     * Adds a step: every text of the next request gets the given vector.
     *
     * @throws IllegalArgumentException if the vector has no dimension
     * @throws IllegalStateException    if the script already ends with {@link #thenRepeats()}
     */
    public MockEmbeddingModel thenEmbeds(float... vector) {
        return add(fixed(vector));
    }

    /**
     * Adds a step: every text of the next request gets the vector computed from it, see
     * {@link #thatEmbeds(Function)}.
     *
     * @throws IllegalStateException if the script already ends with {@link #thenRepeats()}
     */
    public MockEmbeddingModel thenEmbeds(Function<String, float[]> embeddings) {
        return add(computed(embeddings));
    }

    /**
     * Adds a step: the next request is answered with exactly the given response, including its metadata, see
     * {@link #thatResponds(EmbeddingResponse)}.
     *
     * @throws IllegalStateException if the script already ends with {@link #thenRepeats()}
     */
    public MockEmbeddingModel thenResponds(EmbeddingResponse response) {
        return add(responding(response));
    }

    /**
     * Adds a step: the next request fails with exactly the given exception.
     *
     * @throws IllegalStateException if the script already ends with {@link #thenRepeats()}
     */
    public MockEmbeddingModel thenFails(RuntimeException exception) {
        return add(failing(exception));
    }

    /**
     * Answers every further request with the last step of the script. Only the last step repeats; the script does not
     * start over. A last step that computes its vectors computes them again for each request. Without it, a request
     * after the last step fails. It must be the last call of the script.
     *
     * @throws IllegalStateException if the script already ends with {@code thenRepeats()}
     */
    public synchronized MockEmbeddingModel thenRepeats() {
        script.repeatLastStep();
        return this;
    }

    private synchronized MockEmbeddingModel add(Step step) {
        script.add(step);
        return this;
    }

    /**
     * Notifies the given listeners of each call, like a real model does.
     */
    public MockEmbeddingModel withListeners(EmbeddingModelListener... listeners) {
        this.listeners = List.of(listeners);
        return this;
    }

    /**
     * Notifies the given listeners of each call, like a real model does.
     */
    public MockEmbeddingModel withListeners(List<EmbeddingModelListener> listeners) {
        this.listeners = List.copyOf(listeners);
        return this;
    }

    @Override
    public Set<EmbeddingParameter<?>> supportedParameters() {
        return Set.of(EmbeddingRequestParameters.MODEL_NAME, EmbeddingRequestParameters.INPUT_TYPE);
    }

    /**
     * Answers like {@link #doEmbed(EmbeddingRequest)}, with the next step of the script, and completes the future on
     * another thread, where a step that computes its vectors also computes them. It never throws: a {@code null}
     * request gives a failed future.
     */
    @Override
    public CompletableFuture<EmbeddingResponse> doEmbedAsync(EmbeddingRequest request) {
        Step step;
        try {
            step = take(request);
        } catch (RuntimeException e) {
            return CompletableFuture.failedFuture(e);
        }
        return CompletableFuture.supplyAsync(() -> respond(step, request), MockThreads.newThreadPerTask(MOCK_NAME));
    }

    @Override
    public EmbeddingResponse doEmbed(EmbeddingRequest request) {
        return respond(take(request), request);
    }

    /**
     * Records the request and takes the step that answers it, in one go, so that concurrent requests get consistent
     * request numbers. A request after the last step gets a step that fails with an {@link IllegalStateException}.
     *
     * @throws IllegalArgumentException if the request is {@code null}; it is then not recorded
     */
    private synchronized Step take(EmbeddingRequest request) {
        ensureNotNull(request, "request");
        int requestNumber = requests.add(request);
        Step step = script.next();
        return step != null ? step : failing(new IllegalStateException(runOutMessage(request, requestNumber)));
    }

    private static EmbeddingResponse respond(Step step, EmbeddingRequest request) {
        EmbeddingResponse response = step.response();
        if (response != null) {
            int texts = request.inputs().size();
            if (response.embeddings().size() != texts) {
                throw new IllegalStateException("The response of this step has " + response.embeddings().size()
                        + " embeddings, but the request has " + texts + " texts. A provider returns one embedding per"
                        + " text, so give a response with " + texts + ".");
            }
            return response;
        }
        Function<String, float[]> embeddings = embeddingsOf(step);
        List<Embedding> vectors = request.inputs().stream()
                .map(input -> Embedding.from(embeddings.apply(input.text())))
                .toList();
        return EmbeddingResponse.builder()
                .embeddings(vectors)
                .modelName(request.modelName())
                .build();
    }

    private static Function<String, float[]> embeddingsOf(Step step) {
        RuntimeException failure = step.failure();
        if (failure != null) {
            throw failure;
        }
        return ensureNotNull(step.embeddings(), "embeddings");
    }

    private String runOutMessage(EmbeddingRequest request, int requestNumber) {
        List<String> texts = request.inputs().stream().map(input -> input.text()).toList();
        StringBuilder described = new StringBuilder("The texts of request ")
                .append(requestNumber)
                .append(":");
        texts.stream()
                .limit(MAX_TEXTS_SHOWN)
                .forEach(text ->
                        described.append(" \"").append(Script.shortened(text)).append("\""));
        if (texts.size() > MAX_TEXTS_SHOWN) {
            described.append(" and ").append(texts.size() - MAX_TEXTS_SHOWN).append(" more");
        }
        return script.runOutMessage(requestNumber, "", described.toString());
    }

    /**
     * Embeds the text {@code "test"} with the first step of the script that returns vectors, and returns the length of
     * its vector; a step that gives the whole response gives the length of its first embedding. It takes no step and
     * records no request, so the script and {@link #requests()} stay as they are. A script without such a step throws
     * the exception of its first step, if that step fails, or an {@link IllegalStateException}. When an embedding store
     * asks the model for its dimension, for example while a framework starts, set the dimension of the store explicitly
     * instead.
     */
    @Override
    public synchronized int dimension() {
        List<Step> steps = script.steps();
        for (Step step : steps) {
            Function<String, float[]> embeddings = step.embeddings();
            if (embeddings != null) {
                return embeddings.apply("test").length;
            }
            EmbeddingResponse response = step.response();
            if (response != null && !response.embeddings().isEmpty()) {
                return response.embeddings().get(0).dimension();
            }
        }
        RuntimeException failure = steps.get(0).failure();
        if (failure != null) {
            throw failure;
        }
        throw new IllegalStateException("No step of the script returns an embedding, so its dimension is unknown");
    }

    @Override
    public List<EmbeddingModelListener> listeners() {
        return listeners;
    }

    /**
     * A copy of the requests received so far, in the order they arrived.
     */
    public List<EmbeddingRequest> requests() {
        return requests.all();
    }

    /**
     * The only request received so far.
     *
     * @throws IllegalStateException if the mock received no request or several requests
     */
    public EmbeddingRequest request() {
        return requests.only();
    }

    /**
     * The last request received so far.
     *
     * @throws IllegalStateException if the mock received no request
     */
    public EmbeddingRequest lastRequest() {
        return requests.last();
    }

    /**
     * A model that answers the first request with the given vector for every text. All texts are then equally close to
     * each other, so a test with this model cannot depend on their ranking. Add the next steps with {@code then...}
     * methods, or return this vector for every request: {@code thatEmbeds(0.1f, 0.2f).thenRepeats()}.
     *
     * @throws IllegalArgumentException if the vector has no dimension
     */
    public static MockEmbeddingModel thatEmbeds(float... vector) {
        return new MockEmbeddingModel(fixed(vector));
    }

    /**
     * A model that answers the first request with the vector computed from each text, so the test says which texts are
     * close to each other. For example, with
     * <pre>{@code
     * MockEmbeddingModel model = MockEmbeddingModel.thatEmbeds(
     *                 text -> text.contains("refund") ? new float[] {1, 0} : new float[] {0, 1})
     *         .thenRepeats();
     * }</pre>
     * two texts that both contain "refund" get a relevance score of 1, and a text with "refund" and a text without it
     * get a score of 0.5. Add the next steps with {@code then...} methods, or compute the vectors of every request with
     * {@code thenRepeats()}, as above. If the function throws, the request fails with that exception, unchanged.
     */
    public static MockEmbeddingModel thatEmbeds(Function<String, float[]> embeddings) {
        return new MockEmbeddingModel(computed(embeddings));
    }

    /**
     * A model that answers the first request with exactly the given response, including its metadata, for example its
     * token usage or model name, to test code that reads them, such as a cost check:
     * <pre>{@code
     * MockEmbeddingModel model = MockEmbeddingModel.thatResponds(EmbeddingResponse.builder()
     *         .embeddings(List.of(Embedding.from(new float[] {0.1f, 0.2f})))
     *         .tokenUsage(new TokenUsage(12))
     *         .build());
     * }</pre>
     * The response must have one embedding per text of the request, as a provider's response has: otherwise the
     * request fails with an {@link IllegalStateException}. Add the next steps with {@code then...} methods, or answer
     * every request with this response: {@code thatResponds(response).thenRepeats()}.
     */
    public static MockEmbeddingModel thatResponds(EmbeddingResponse response) {
        return new MockEmbeddingModel(responding(response));
    }

    /**
     * A model whose first request fails with exactly the given exception. To let every request fail, add
     * {@link #thenRepeats()}. To let later requests succeed, for example to test your own retry logic, add steps with
     * {@code then...} methods.
     */
    public static MockEmbeddingModel thatFails(RuntimeException exception) {
        return new MockEmbeddingModel(failing(exception));
    }

    private static Step fixed(float[] vector) {
        ensureNotNull(vector, "vector");
        if (vector.length == 0) {
            throw new IllegalArgumentException("thatEmbeds(...) and thenEmbeds(...) need a vector with at least one"
                    + " dimension");
        }
        float[] copy = vector.clone();
        return new Step(text -> copy.clone(), null, null);
    }

    private static Step computed(Function<String, float[]> embeddings) {
        return new Step(ensureNotNull(embeddings, "embeddings"), null, null);
    }

    private static Step responding(EmbeddingResponse response) {
        return new Step(null, ensureNotNull(response, "response"), null);
    }

    private static Step failing(RuntimeException exception) {
        return new Step(null, null, ensureNotNull(exception, "exception"));
    }
}
