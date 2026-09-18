package dev.langchain4j.model.embedding.request;

import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;

import dev.langchain4j.Experimental;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.segment.TextSegment;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A request to an {@link dev.langchain4j.model.embedding.EmbeddingModel}, containing a batch of
 * {@link EmbeddingInput}s to embed and the per-call {@link EmbeddingRequestParameters parameters}.
 * <p>
 * The request has two dimensions:
 * <ul>
 *     <li>the <b>batch</b> dimension — the list of {@link EmbeddingInput}s, each producing one embedding;</li>
 *     <li>the <b>interleaving</b> dimension — the list of {@link Content} parts inside each {@link EmbeddingInput},
 *         fused into a single embedding (for multimodal models).</li>
 * </ul>
 * For the common text case the builder accepts plain {@link String}s and {@link TextSegment}s directly, each
 * becoming a single-text {@link EmbeddingInput}; the {@link Content}-based methods unlock multimodal input.
 *
 * @since 1.18.0
 */
@Experimental
public class EmbeddingRequest {

    private final List<EmbeddingInput> inputs;
    private final EmbeddingRequestParameters parameters;

    protected EmbeddingRequest(Builder builder) {
        this.inputs = copy(ensureNotEmpty(builder.inputs, "inputs"));
        this.parameters = getOrDefault(builder.parameters(), EmbeddingRequestParameters.EMPTY);
    }

    /**
     * The batch of inputs to embed; each {@link EmbeddingInput} yields one embedding.
     */
    public List<EmbeddingInput> inputs() {
        return inputs;
    }

    /**
     * The per-call parameters; never {@code null} ({@link EmbeddingRequestParameters#EMPTY} when none were set).
     */
    public EmbeddingRequestParameters parameters() {
        return parameters;
    }

    public String modelName() {
        return parameters.modelName();
    }

    public Integer dimensions() {
        return parameters.dimensions();
    }

    public EmbeddingInputType inputType() {
        return parameters.inputType();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EmbeddingRequest that = (EmbeddingRequest) o;
        return Objects.equals(inputs, that.inputs) && Objects.equals(parameters, that.parameters);
    }

    @Override
    public int hashCode() {
        return Objects.hash(inputs, parameters);
    }

    @Override
    public String toString() {
        return "EmbeddingRequest{inputs=" + inputs + ", parameters=" + parameters + '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private final List<EmbeddingInput> inputs = new ArrayList<>();
        private EmbeddingRequestParameters parameters;
        private final DefaultEmbeddingRequestParameters.Builder<?> overrides = EmbeddingRequestParameters.builder();
        private boolean overridesUsed = false;

        // ---------- text convenience (each becomes a single-text EmbeddingInput) ----------

        public Builder input(String text) {
            this.inputs.add(EmbeddingInput.from(text));
            return this;
        }

        public Builder inputs(String... texts) {
            for (String text : texts) {
                this.inputs.add(EmbeddingInput.from(text));
            }
            return this;
        }

        public Builder textSegment(TextSegment segment) {
            if (segment != null) {
                this.inputs.add(EmbeddingInput.from(segment));
            }
            return this;
        }

        public Builder textSegments(List<TextSegment> segments) {
            if (segments != null) {
                for (TextSegment segment : segments) {
                    textSegment(segment);
                }
            }
            return this;
        }

        // ---------- multimodal: content-based inputs ----------

        /**
         * Adds one input made of the given (possibly interleaved) content parts, fused into a single embedding.
         */
        public Builder input(Content... contents) {
            this.inputs.add(EmbeddingInput.from(contents));
            return this;
        }

        public Builder input(EmbeddingInput input) {
            if (input != null) {
                this.inputs.add(input);
            }
            return this;
        }

        public Builder inputs(EmbeddingInput... inputs) {
            for (EmbeddingInput input : inputs) {
                input(input);
            }
            return this;
        }

        public Builder inputs(List<EmbeddingInput> inputs) {
            if (inputs != null) {
                this.inputs.addAll(inputs);
            }
            return this;
        }

        // ---------- parameters ----------

        public Builder parameters(EmbeddingRequestParameters parameters) {
            this.parameters = parameters;
            return this;
        }

        /**
         * Convenience setter that overrides {@link EmbeddingRequestParameters#MODEL_NAME} on top of any
         * {@link #parameters(EmbeddingRequestParameters) parameters} set explicitly.
         */
        public Builder modelName(String modelName) {
            this.overrides.modelName(modelName);
            this.overridesUsed = true;
            return this;
        }

        /**
         * Convenience setter that overrides {@link EmbeddingRequestParameters#DIMENSIONS} on top of any
         * {@link #parameters(EmbeddingRequestParameters) parameters} set explicitly.
         */
        public Builder dimensions(Integer dimensions) {
            this.overrides.dimensions(dimensions);
            this.overridesUsed = true;
            return this;
        }

        /**
         * Convenience setter that overrides {@link EmbeddingRequestParameters#INPUT_TYPE} on top of any
         * {@link #parameters(EmbeddingRequestParameters) parameters} set explicitly.
         */
        public Builder inputType(EmbeddingInputType inputType) {
            this.overrides.inputType(inputType);
            this.overridesUsed = true;
            return this;
        }

        private EmbeddingRequestParameters parameters() {
            EmbeddingRequestParameters base = getOrDefault(parameters, EmbeddingRequestParameters.EMPTY);
            return overridesUsed ? base.overrideWith(overrides.build()) : base;
        }

        public EmbeddingRequest build() {
            return new EmbeddingRequest(this);
        }
    }
}
