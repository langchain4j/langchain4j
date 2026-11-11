package dev.langchain4j.test.mock;

import static dev.langchain4j.internal.ValidationUtils.ensureGreaterThanZero;
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
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * An {@link EmbeddingModel} for tests: it needs no network and records the requests it receives. It supports both
 * {@link #embed(String)} and {@link #embedAsync(EmbeddingRequest)}, which completes on another thread. It accepts the
 * per-call parameters {@code modelName} and {@code inputType}, and rejects {@code dimensions} with an
 * {@code UnsupportedFeatureException}, like a model without that parameter. A response reports the model name of the
 * request.
 * <p>
 * Use {@link #thatEmbedsByWords()} when the test depends on which texts are close to each other, for example on the
 * ranking of retrieved segments or on a minimum score, and {@link #thatEmbeds(float...)} when it does not matter.
 * When a test depends on the meaning of texts, use a real embedding model that runs in-process, for example
 * {@code AllMiniLmL6V2QuantizedEmbeddingModel} from the module {@code langchain4j-embeddings-all-minilm-l6-v2-q}.
 */
@Experimental
public class MockEmbeddingModel implements EmbeddingModel {

    private static final int DEFAULT_DIMENSION = 384;
    private static final Pattern WORD_SEPARATORS = Pattern.compile("[^\\p{L}\\p{M}\\p{N}]+");

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
     * A model that computes the vector from each text.
     */
    public static MockEmbeddingModel thatEmbeds(Function<String, float[]> embeddings) {
        return new MockEmbeddingModel(embeddings);
    }

    /**
     * A model with vectors of 384 dimensions that are close when texts share words, see
     * {@link #thatEmbedsByWords(int)}.
     */
    public static MockEmbeddingModel thatEmbedsByWords() {
        return thatEmbedsByWords(DEFAULT_DIMENSION);
    }

    /**
     * A model whose vectors are close when texts share words, so that a test can check the ranking of retrieved
     * segments or a minimum score, always with the same result.
     * <p>
     * The text is lower-cased and split into words. The hash of each word picks one position in the vector, and that
     * position is increased by one. Finally, the vector is scaled to a length of 1. Two short texts that share no words
     * get a cosine similarity of 0, which is a relevance score of 0.5. A text without letters or digits, for example
     * {@code "?"}, gets a vector of zeros.
     * <p>
     * Every word counts equally, also common ones such as "the" and "is", and only whole words match: "cancelled" is not
     * "cancel", and "dog" is not "pet". Two different words can pick the same position by chance, more often in longer
     * texts, which raises the score slightly: use a larger {@code dimension} to make this less likely, and do not expect
     * exactly 0.5 for long unrelated texts. When a test depends on the meaning of texts, use a real embedding model that
     * runs in-process, see {@link MockEmbeddingModel}.
     *
     * @param dimension the number of dimensions of the vectors
     */
    public static MockEmbeddingModel thatEmbedsByWords(int dimension) {
        ensureGreaterThanZero(dimension, "dimension");
        return new MockEmbeddingModel(text -> embedByWords(text, dimension));
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

    private static float[] embedByWords(String text, int dimension) {
        float[] vector = new float[dimension];
        for (String word : WORD_SEPARATORS.split(text.toLowerCase(Locale.ROOT))) {
            if (!word.isEmpty()) {
                vector[Math.floorMod(word.hashCode(), dimension)] += 1f;
            }
        }
        Embedding embedding = Embedding.from(vector);
        embedding.normalize();
        return embedding.vector();
    }
}
