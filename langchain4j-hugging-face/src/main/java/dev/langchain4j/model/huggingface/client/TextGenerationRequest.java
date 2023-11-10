package dev.langchain4j.model.huggingface.client;

import java.util.Objects;

import static dev.langchain4j.internal.Utils.quoted;

public class TextGenerationRequest {

    private final String inputs;
    private final Parameters parameters;
    private final Options options;

    TextGenerationRequest(Builder builder) {
        this.inputs = builder.inputs;
        this.parameters = builder.parameters;
        this.options = builder.options;
    }

    @Override
    public boolean equals(Object another) {
        if (this == another) return true;
        return another instanceof TextGenerationRequest
                && equalTo((TextGenerationRequest) another);
    }

    private boolean equalTo(TextGenerationRequest another) {
        return Objects.equals(inputs, another.inputs)
                && Objects.equals(parameters, another.parameters)
                && Objects.equals(options, another.options);
    }

    @Override
    public int hashCode() {
        int h = 5381;
        h += (h << 5) + Objects.hashCode(inputs);
        h += (h << 5) + Objects.hashCode(parameters);
        h += (h << 5) + Objects.hashCode(options);
        return h;
    }

    @Override
    public String toString() {
        return "TextGenerationRequest {"
                + " inputs = " + quoted(inputs)
                + ", parameters = " + parameters
                + ", options = " + options
                + " }";
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private String inputs;
        private Parameters parameters;
        private Options options;

        public Builder inputs(String inputs) {
            this.inputs = inputs;
            return this;
        }

        public Builder parameters(Parameters parameters) {
            this.parameters = parameters;
            return this;
        }

        public Builder options(Options options) {
            this.options = options;
            return this;
        }

        public TextGenerationRequest build() {
            return new TextGenerationRequest(this);
        }
    }
}
