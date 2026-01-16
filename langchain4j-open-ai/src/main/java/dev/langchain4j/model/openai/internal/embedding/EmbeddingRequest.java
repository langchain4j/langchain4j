package dev.langchain4j.model.openai.internal.embedding;

import static java.util.Arrays.asList;
import static java.util.Collections.unmodifiableList;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import java.util.List;
import java.util.Objects;

@JsonDeserialize(builder = EmbeddingRequest.Builder.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public final class EmbeddingRequest {

    @JsonProperty
    private final String model;

    @JsonProperty
    private final List<String> input;

    @JsonProperty
    private final Integer dimensions;

    @JsonProperty
    private final String user;

    @JsonProperty
    private final String encodingFormat;

    public EmbeddingRequest(Builder builder) {
        this.model = builder.model;
        this.input = builder.input;
        this.dimensions = builder.dimensions;
        this.user = builder.user;
        this.encodingFormat = builder.encodingFormat;
    }

    public String model() {
        return model;
    }

    public List<String> input() {
        return input;
    }

    public Integer dimensions() {
        return dimensions;
    }

    public String user() {
        return user;
    }

    public String encodingFormat() {
        return encodingFormat;
    }

    @Override
    public boolean equals(Object another) {
        if (this == another) return true;
        return another instanceof EmbeddingRequest && equalTo((EmbeddingRequest) another);
    }

    private boolean equalTo(EmbeddingRequest another) {
        return Objects.equals(model, another.model)
                && Objects.equals(input, another.input)
                && Objects.equals(dimensions, another.dimensions)
                && Objects.equals(user, another.user)
                && Objects.equals(encodingFormat, another.encodingFormat);
    }

    @Override
    public int hashCode() {
        int h = 5381;
        h += (h << 5) + Objects.hashCode(model);
        h += (h << 5) + Objects.hashCode(input);
        h += (h << 5) + Objects.hashCode(dimensions);
        h += (h << 5) + Objects.hashCode(user);
        h += (h << 5) + Objects.hashCode(encodingFormat);
        return h;
    }

    @Override
    public String toString() {
        return "EmbeddingRequest{"
                + "model=" + model
                + ", input=" + input
                + ", dimensions=" + dimensions
                + ", user=" + user
                + ", encodingFormat=" + encodingFormat
                + "}";
    }

    public static Builder builder() {
        return new Builder();
    }

    @JsonPOJOBuilder(withPrefix = "")
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static final class Builder {

        private String model;
        private List<String> input;
        private Integer dimensions;
        private String user;
        private String encodingFormat;

        public Builder model(String model) {
            this.model = model;
            return this;
        }

        public Builder input(String... input) {
            return input(asList(input));
        }

        public Builder input(List<String> input) {
            if (input != null) {
                this.input = unmodifiableList(input);
            }
            return this;
        }

        public Builder dimensions(Integer dimensions) {
            this.dimensions = dimensions;
            return this;
        }

        public Builder user(String user) {
            this.user = user;
            return this;
        }

        public Builder encodingFormat(String encodingFormat) {
            this.encodingFormat = encodingFormat;
            return this;
        }

        public EmbeddingRequest build() {
            return new EmbeddingRequest(this);
        }
    }
}
