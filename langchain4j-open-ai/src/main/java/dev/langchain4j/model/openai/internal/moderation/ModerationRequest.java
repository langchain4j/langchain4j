package dev.langchain4j.model.openai.internal.moderation;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import java.util.List;
import java.util.Objects;

import static java.util.Collections.singletonList;
import static java.util.Collections.unmodifiableList;

@JsonDeserialize(builder = ModerationRequest.Builder.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class ModerationRequest {

    @JsonProperty
    private final String model;
    @JsonProperty
    private final List<String> input;

    public ModerationRequest(Builder builder) {
        this.model = builder.model;
        this.input = builder.input;
    }

    public String model() {
        return model;
    }

    public List<String> input() {
        return input;
    }

    @Override
    public boolean equals(Object another) {
        if (this == another) return true;
        return another instanceof ModerationRequest
                && equalTo((ModerationRequest) another);
    }

    private boolean equalTo(ModerationRequest another) {
        return Objects.equals(model, another.model)
                && Objects.equals(input, another.input);
    }

    @Override
    public int hashCode() {
        int h = 5381;
        h += (h << 5) + Objects.hashCode(model);
        h += (h << 5) + Objects.hashCode(input);
        return h;
    }

    @Override
    public String toString() {
        return "ModerationRequest{"
                + "model=" + model
                + ", input=" + input
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

        public Builder model(String model) {
            this.model = model;
            return this;
        }

        public Builder input(List<String> input) {
            if (input != null) {
                this.input = unmodifiableList(input);
            }
            return this;
        }

        public Builder input(String input) {
            return input(singletonList(input));
        }

        public ModerationRequest build() {
            return new ModerationRequest(this);
        }
    }
}
