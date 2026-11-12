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

/**
 * An {@link EmbeddingModel} for tests: it needs no network and records the requests it receives. It supports both
 * {@link #embed(String)} and {@link #embedAsync(EmbeddingRequest)}, which completes on another thread. It accepts the
 * per-call parameters {@code modelName} and {@code inputType}, and rejects {@code dimensions} with an
 * {@code UnsupportedFeatureException}, like a model without that parameter. A response reports the model name of the
 * request.
 * <p>
 * Use {@link #thatEmbeds(Function)} when the test depends on which texts are close to each other, for example on the
 * ranking of retrieved segments or on a minimum score: the function in the test then says which texts are close. Use
 * {@link #thatEmbeds(float...)} when it does not matter. When a test depends on the meaning of texts, use a real
 * embedding model that runs in-process, for example {@code AllMiniLmL6V2QuantizedEmbeddingModel} from the module
 * {@code langchain4j-embeddings-all-minilm-l6-v2-q}.
 */
@Experimental
public class MockEmbeddingModel implements EmbeddingModel {

    private final Function<String, float[]> embeddings;
    private final RecordedRequests<EmbeddingRequest> requests = new RecordedRequests<>("embedding");
    private List<EmbeddingModelListener> listeners = List.of();

    MockEmbeddingModel(Function<String, float[]> embeddings) {
        this.embeddings = ensureNotNull(embeddings, "embeddings");
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

    @Override
    public CompletableFuture<EmbeddingResponse> doEmbedAsync(EmbeddingRequest request) {
        requests.add(request);
        return CompletableFuture.supplyAsync(
                () -> respond(request), MockThreads.newThreadPerTask("MockEmbeddingModel"));
    }

    @Override
    public EmbeddingResponse doEmbed(EmbeddingRequest request) {
        requests.add(request);
        return respond(request);
    }

    private EmbeddingResponse respond(EmbeddingRequest request) {
        List<Embedding> vectors = request.inputs().stream()
                .map(input -> Embedding.from(embeddings.apply(input.text())))
                .toList();
        return EmbeddingResponse.builder()
                .embeddings(vectors)
                .modelName(request.modelName())
                .build();
    }

    /**
     * Embeds the text {@code "test"} the same way as every other text and returns the length of its vector, so a mock
     * created with {@link #thatFails(RuntimeException)} throws here too. When an embedding store asks the model for its
     * dimension, for example while a framework starts, set the dimension of the store explicitly instead.
     */
    @Override
    public int dimension() {
        return embeddings.apply("test").length;
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
     * A model that returns the given vector for every text. All texts are then equally close to each other, so a test
     * with this model cannot depend on their ranking.
     */
    public static MockEmbeddingModel thatEmbeds(float... vector) {
        ensureNotNull(vector, "vector");
        if (vector.length == 0) {
            throw new IllegalArgumentException("thatEmbeds(...) needs a vector with at least one dimension");
        }
        float[] copy = vector.clone();
        return new MockEmbeddingModel(text -> copy.clone());
    }

    /**
     * A model that computes the vector from each text, so the test says which texts are close to each other. For
     * example, with
     * <pre>{@code
     * MockEmbeddingModel model = MockEmbeddingModel.thatEmbeds(
     *         text -> text.contains("refund") ? new float[] {1, 0} : new float[] {0, 1});
     * }</pre>
     * two texts that both contain "refund" get a relevance score of 1, and a text with "refund" and a text without it
     * get a score of 0.5.
     */
    public static MockEmbeddingModel thatEmbeds(Function<String, float[]> embeddings) {
        return new MockEmbeddingModel(embeddings);
    }

    /**
     * A model whose requests all fail with exactly the given exception.
     */
    public static MockEmbeddingModel thatFails(RuntimeException exception) {
        ensureNotNull(exception, "exception");
        return new MockEmbeddingModel(text -> {
            throw exception;
        });
    }
}
