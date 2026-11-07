package dev.langchain4j.model.openai.internal.chat;

import static java.util.Collections.unmodifiableList;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import dev.langchain4j.internal.JacocoIgnoreCoverageGenerated;
import java.util.List;
import java.util.Objects;

@JsonDeserialize(builder = LogProb.Builder.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class LogProb {

    @JsonProperty
    private final String token;

    @JsonProperty
    private final Double logprob;

    @JsonProperty
    private final List<Integer> bytes;

    @JsonProperty
    private final List<LogProb> topLogprobs;

    @JsonCreator
    public LogProb(Builder builder) {
        this.token = builder.token;
        this.logprob = builder.logprob;
        this.bytes = builder.bytes == null ? null : unmodifiableList(builder.bytes);
        this.topLogprobs = builder.topLogprobs == null ? null : unmodifiableList(builder.topLogprobs);
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

    public List<LogProb> topLogprobs() {
        return topLogprobs;
    }

    @Override
    @JacocoIgnoreCoverageGenerated
    public boolean equals(Object another) {
        if (this == another) return true;
        return another instanceof LogProb && equalTo((LogProb) another);
    }

    @JacocoIgnoreCoverageGenerated
    private boolean equalTo(LogProb another) {
        return Objects.equals(token, another.token)
                && Objects.equals(logprob, another.logprob)
                && Objects.equals(bytes, another.bytes)
                && Objects.equals(topLogprobs, another.topLogprobs);
    }

    @Override
    @JacocoIgnoreCoverageGenerated
    public int hashCode() {
        int h = 5381;
        h += (h << 5) + Objects.hashCode(token);
        h += (h << 5) + Objects.hashCode(logprob);
        h += (h << 5) + Objects.hashCode(bytes);
        h += (h << 5) + Objects.hashCode(topLogprobs);
        return h;
    }

    @Override
    @JacocoIgnoreCoverageGenerated
    public String toString() {
        return "LogProb{"
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
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
    public static final class Builder {

        private String token;
        private Double logprob;
        private List<Integer> bytes;
        private List<LogProb> topLogprobs;

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
                this.bytes = bytes;
            }
            return this;
        }

        public Builder topLogprobs(List<LogProb> topLogprobs) {
            if (topLogprobs != null) {
                this.topLogprobs = topLogprobs;
            }
            return this;
        }

        public LogProb build() {
            return new LogProb(this);
        }
    }
}
