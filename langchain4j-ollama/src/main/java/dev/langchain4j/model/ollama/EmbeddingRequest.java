package dev.langchain4j.model.ollama;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.List;

/**
 * Request payload for Ollama's embedding generation API.
 *
 * <p>
 * This class represents the JSON request body sent to the
 * {@code POST /api/embed} endpoint for generating vector embeddings
 * from one or more input texts.
 * </p>
 *
 * <p>
 * Example request:
 * </p>
 *
 * <pre>
 * curl http://localhost:11434/api/embed -d '{
 *   "model": "all-minilm",
 *   "input": [
 *     "Why is the sky blue?",
 *     "What is the best place to visit in summer"
 *   ],
 *   "dimensions": 2
 * }'
 * </pre>
 *
 * <p>
 * Example response:
 * </p>
 *
 * <pre>
 * {
 *   "model": "all-minilm",
 *   "embeddings": [
 *     [0.98507947, -0.1721],
 *     [0.9774056, -0.21137223]
 *   ],
 *   "total_duration": 9305867458,
 *   "load_duration": 9067644458,
 *   "prompt_eval_count": 19
 * }
 * </pre>
 *
 * <p>
 * The {@code dimensions} field is optional and controls the number of
 * dimensions in the returned embedding vectors for models that support
 * configurable output dimensionality.
 * </p>
 *
 * @see <a href="https://github.com/ollama/ollama/blob/main/docs/api.md">
 * Ollama API Documentation
 * </a>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(NON_NULL)
@JsonNaming(SnakeCaseStrategy.class)
class EmbeddingRequest {

    private String model;
    private List<String> input;

    /**
     * Sets the number of dimensions for the generated embedding vector.
     * <p>
     * This value is sent as the {@code dimensions} field in the JSON request
     * payload to Ollama's {@code /api/embed} endpoint.
     * </p>
     *
     * <p>
     * When specified, Ollama may return embedding vectors with the requested
     * number of dimensions, depending on whether the selected embedding model
     * supports configurable output dimensions.
     * </p>
     *
     * <p>
     * If {@code null}, the model's default embedding size is used.
     * </p>
     *
     * <p>
     * Example:
     * <pre>
     * {
     *   "model": "all-minilm",
     *   "input": ["Why is the sky blue?", "What is the best place to visit in summer"],
     *   "dimensions": 256
     * }
     * </pre>
     * </p>
     *
     * @param dimensions the requested output embedding vector size
     * @return this builder instance
     */
    private Integer dimensions;

    EmbeddingRequest() {}

    EmbeddingRequest(String model, List<String> input) {
        this(model, input, null);
    }

    EmbeddingRequest(String model, List<String> input, Integer dimensions) {
        this.model = model;
        this.input = input;
        this.dimensions = dimensions;
    }

    static Builder builder() {
        return new Builder();
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public List<String> getInput() {
        return input;
    }

    public void setInput(List<String> input) {
        this.input = input;
    }

    public Integer getDimensions() {
        return dimensions;
    }

    public void setDimension(Integer dimensions) {
        this.dimensions = dimensions;
    }

    static class Builder {

        private String model;
        private List<String> input;
        private Integer dimensions;

        Builder model(String model) {
            this.model = model;
            return this;
        }

        Builder input(List<String> input) {
            this.input = input;
            return this;
        }

        Builder dimensions(Integer dimensions) {
            this.dimensions = dimensions;
            return this;
        }

        EmbeddingRequest build() {
            return new EmbeddingRequest(model, input, dimensions);
        }
    }
}
