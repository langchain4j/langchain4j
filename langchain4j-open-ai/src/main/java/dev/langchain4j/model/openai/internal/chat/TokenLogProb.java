package dev.langchain4j.model.openai.internal.chat;

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

@JsonDeserialize(builder = TokenLogProb.Builder.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public final class TokenLogProb {

    @JsonProperty
    private final String token;

    @JsonProperty
    private final Double logprob;

    @JsonProperty
    private final List<Integer> bytes;

    @JsonProperty
    private final List<TopLogProb> topLogprobs;

    public TokenLogProb(Builder builder) {
        this.token = builder.token;
        this.logprob = builder.logprob;
        this.bytes = builder.bytes;
        this.topLogprobs = builder.topLogprobs;
    }

    public String token() {
        return token;
    }

    public Double logprob() {
        return logprob;
    }

    public List<Integer> bytes() {
        return bytes;
    }

    public List<TopLogProb> topLogprobs() {
        return topLogprobs;
    }

    @Override
    public boolean equals(Object another) {
        if (this == another) return true;
        return another instanceof TokenLogProb && equalTo((TokenLogProb) another);
    }

    private boolean equalTo(TokenLogProb another) {
        return Objects.equals(token, another.token)
                && Objects.equals(logprob, another.logprob)
                && Objects.equals(bytes, another.bytes)
                && Objects.equals(topLogprobs, another.topLogprobs);
    }

    @Override
    public int hashCode() {
        int h = 5381;
        h += (h << 5) + Objects.hashCode(token);
        h += (h << 5) + Objects.hashCode(logprob);
        h += (h << 5) + Objects.hashCode(bytes);
        h += (h << 5) + Objects.hashCode(topLogprobs);
        return h;
    }

    @Override
    public String toString() {
        return "TokenLogProb{"
                + "token=" + token
                + ", logprob=" + logprob
                + ", bytes=" + bytes
                + ", topLogprobs=" + topLogprobs
                + "}";
    }

    public static Builder builder() {
        return new Builder();
    }

    @JsonPOJOBuilder(withPrefix = "")
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static final class Builder {

        private String token;
        private Double logprob;
        private List<Integer> bytes;
        private List<TopLogProb> topLogprobs;

        public Builder token(String token) {
            this.token = token;
            return this;
        }

        public Builder logprob(Double logprob) {
            this.logprob = logprob;
            return this;
        }

        public Builder bytes(List<Integer> bytes) {
            if (bytes != null) {
                this.bytes = unmodifiableList(bytes);
            }
            return this;
        }

        public Builder topLogprobs(List<TopLogProb> topLogprobs) {
            if (topLogprobs != null) {
                this.topLogprobs = unmodifiableList(topLogprobs);
            }
            return this;
        }

        public TokenLogProb build() {
            return new TokenLogProb(this);
        }
    }
}
