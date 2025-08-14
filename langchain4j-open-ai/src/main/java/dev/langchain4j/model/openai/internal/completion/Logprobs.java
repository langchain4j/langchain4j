package dev.langchain4j.model.openai.internal.completion;


import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static java.util.Collections.unmodifiableList;
import static java.util.Collections.unmodifiableMap;

@JsonDeserialize(builder = Logprobs.Builder.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public final class Logprobs {

    @JsonProperty
    private final List<String> tokens;
    @JsonProperty
    private final List<Double> tokenLogprobs;
    @JsonProperty
    private final List<Map<String, Double>> topLogprobs;
    @JsonProperty
    private final List<Integer> textOffset;

    public Logprobs(Builder builder) {
        this.tokens = builder.tokens;
        this.tokenLogprobs = builder.tokenLogprobs;
        this.topLogprobs = builder.topLogprobs;
        this.textOffset = builder.textOffset;
    }

    public List<String> tokens() {
        return tokens;
    }

    public List<Double> tokenLogprobs() {
        return tokenLogprobs;
    }

    public List<Map<String, Double>> topLogprobs() {
        return topLogprobs;
    }

    public List<Integer> textOffset() {
        return textOffset;
    }

    @Override
    public boolean equals(Object another) {
        if (this == another) return true;
        return another instanceof Logprobs
                && equalTo((Logprobs) another);
    }

    private boolean equalTo(Logprobs another) {
        return Objects.equals(tokens, another.tokens)
                && Objects.equals(tokenLogprobs, another.tokenLogprobs)
                && Objects.equals(topLogprobs, another.topLogprobs)
                && Objects.equals(textOffset, another.textOffset);
    }

    @Override
    public int hashCode() {
        int h = 5381;
        h += (h << 5) + Objects.hashCode(tokens);
        h += (h << 5) + Objects.hashCode(tokenLogprobs);
        h += (h << 5) + Objects.hashCode(topLogprobs);
        h += (h << 5) + Objects.hashCode(textOffset);
        return h;
    }

    @Override
    public String toString() {
        return "Logprobs{"
                + "tokens=" + tokens
                + ", tokenLogprobs=" + tokenLogprobs
                + ", topLogprobs=" + topLogprobs
                + ", textOffset=" + textOffset
                + "}";
    }

    public static Builder builder() {
        return new Builder();
    }

    @JsonPOJOBuilder(withPrefix = "")
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static final class Builder {

        private List<String> tokens;
        private List<Double> tokenLogprobs;
        private List<Map<String, Double>> topLogprobs;
        private List<Integer> textOffset;

        public Builder tokens(List<String> tokens) {
            if (tokens != null) {
                this.tokens = unmodifiableList(tokens);
            }
            return this;
        }

        public Builder tokenLogprobs(List<Double> tokenLogprobs) {
            if (tokenLogprobs != null) {
                this.tokenLogprobs = unmodifiableList(tokenLogprobs);
            }
            return this;
        }

        public Builder topLogprobs(List<Map<String, Double>> topLogprobs) {
            if (topLogprobs != null) {
                List<Map<String, Double>> topLogprobsCopy = new ArrayList<>();
                for (Map<String, Double> map : topLogprobs) {
                    topLogprobsCopy.add(unmodifiableMap(map));
                }
                this.topLogprobs = unmodifiableList(topLogprobsCopy);
            }

            return this;
        }

        public Builder textOffset(List<Integer> textOffset) {
            if (textOffset != null) {
                this.textOffset = unmodifiableList(textOffset);
            }
            return this;
        }

        public Logprobs build() {
            return new Logprobs(this);
        }
    }
}
