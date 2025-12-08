package dev.langchain4j.model.huggingface.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.Objects;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;
import static dev.langchain4j.internal.Utils.quoted;

@Deprecated(forRemoval = true, since = "1.7.0-beta13")
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(NON_NULL)
@JsonNaming(SnakeCaseStrategy.class)
public class TextGenerationRequest {

    private final String inputs;
    private final Parameters parameters;
    private final Options options;

    TextGenerationRequest(Builder builder) {
        this.inputs = builder.inputs;
        this.parameters = builder.parameters;
        this.options = builder.options;
    }

    public String getInputs() {
        return inputs;
    }

    public Parameters getParameters() {
        return parameters;
    }

    public Options getOptions() {
        return options;
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
