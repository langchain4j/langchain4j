package dev.langchain4j.model.embedding.request;

import dev.langchain4j.Experimental;
import java.util.Set;

/**
 * The per-call parameters of an {@link EmbeddingRequest}, such as {@link #dimensions()} or {@link #inputType()}.
 * Provider integrations can extend this interface (via {@link DefaultEmbeddingRequestParameters}) to add
 * provider-specific parameters.
 * <p>
 * Every value carried by these parameters is identified by an {@link EmbeddingParameter} token. The set of
 * tokens actually populated is exposed via {@link #presentParameters()}; an
 * {@link dev.langchain4j.model.embedding.EmbeddingModel} rejects a request carrying a parameter it does not
 * list in {@link dev.langchain4j.model.embedding.EmbeddingModel#supportedParameters()}, so a parameter a model
 * cannot apply is never silently ignored.
 *
 * @see DefaultEmbeddingRequestParameters
 * @since 1.18.0
 */
@Experimental
public interface EmbeddingRequestParameters {

    /**
     * A token identifying the name of the model to use for this request.
     */
    EmbeddingParameter<String> MODEL_NAME = new EmbeddingParameter<>("modelName", String.class);

    /**
     * A token identifying the number of dimensions the resulting embeddings should have,
     * for providers that support reducing the output dimensionality.
     */
    EmbeddingParameter<Integer> DIMENSIONS = new EmbeddingParameter<>("dimensions", Integer.class);

    /**
     * A token identifying the {@link EmbeddingInputType} (query vs document), for providers that encode
     * queries and documents differently.
     */
    EmbeddingParameter<EmbeddingInputType> INPUT_TYPE = new EmbeddingParameter<>("inputType", EmbeddingInputType.class);

    /**
     * Empty parameters: nothing is populated, so {@link #presentParameters()} is empty.
     */
    EmbeddingRequestParameters EMPTY = DefaultEmbeddingRequestParameters.builder().build();

    String modelName();

    Integer dimensions();

    EmbeddingInputType inputType();

    /**
     * Returns the value of the given parameter, or {@code null} if it is not populated.
     *
     * @param parameter the parameter token.
     * @param <T>       the value type.
     * @return the value, or {@code null}.
     */
    <T> T parameter(EmbeddingParameter<T> parameter);

    /**
     * Returns the set of parameters that are actually populated (non-null) in this instance.
     * This is what an {@link dev.langchain4j.model.embedding.EmbeddingModel} validates against its
     * {@link dev.langchain4j.model.embedding.EmbeddingModel#supportedParameters()}.
     *
     * @return the populated parameters; never {@code null}.
     */
    Set<EmbeddingParameter<?>> presentParameters();

    /**
     * Creates a new {@link EmbeddingRequestParameters} by combining the current parameters with the specified ones.
     * Values from the specified parameters override values from the current parameters when there is overlap.
     * Neither instance is modified.
     *
     * @param that the parameters whose values will override the current ones.
     * @return a new combined {@link EmbeddingRequestParameters} instance.
     */
    EmbeddingRequestParameters overrideWith(EmbeddingRequestParameters that);

    static DefaultEmbeddingRequestParameters.Builder<?> builder() {
        return new DefaultEmbeddingRequestParameters.Builder<>();
    }
}
