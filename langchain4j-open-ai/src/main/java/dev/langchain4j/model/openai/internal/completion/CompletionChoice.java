package dev.langchain4j.model.openai.internal.completion;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import java.util.Objects;

@JsonDeserialize(builder = CompletionChoice.Builder.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public final class CompletionChoice {

    @JsonProperty
    private final String text;
    @JsonProperty
    private final Integer index;
    @JsonProperty
    private final Logprobs logprobs;
    @JsonProperty
    private final String finishReason;

    public CompletionChoice(Builder builder) {
        this.text = builder.text;
        this.index = builder.index;
        this.logprobs = builder.logprobs;
        this.finishReason = builder.finishReason;
    }

    public String text() {
        return text;
    }

    public Integer index() {
        return index;
    }

    public Logprobs logprobs() {
        return logprobs;
    }

    public String finishReason() {
        return finishReason;
    }

    @Override
    public boolean equals(Object another) {
        if (this == another) return true;
        return another instanceof CompletionChoice
                && equalTo((CompletionChoice) another);
    }

    private boolean equalTo(CompletionChoice another) {
        return Objects.equals(text, another.text)
                && Objects.equals(index, another.index)
                && Objects.equals(logprobs, another.logprobs)
                && Objects.equals(finishReason, another.finishReason);
    }

    @Override
    public int hashCode() {
        int h = 5381;
        h += (h << 5) + Objects.hashCode(text);
        h += (h << 5) + Objects.hashCode(index);
        h += (h << 5) + Objects.hashCode(logprobs);
        h += (h << 5) + Objects.hashCode(finishReason);
        return h;
    }

    @Override
    public String toString() {
        return "CompletionChoice{"
                + "text=" + text
                + ", index=" + index
                + ", logprobs=" + logprobs
                + ", finishReason=" + finishReason
                + "}";
    }

    public static Builder builder() {
        return new Builder();
    }

    @JsonPOJOBuilder(withPrefix = "")
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static final class Builder {

        @JsonProperty
        private String text;
        @JsonProperty
        private Integer index;
        @JsonProperty
        private Logprobs logprobs;
        @JsonProperty
        private String finishReason;

        public Builder text(String text) {
            this.text = text;
            return this;
        }

        public Builder index(Integer index) {
            this.index = index;
            return this;
        }

        public Builder logprobs(Logprobs logprobs) {
            this.logprobs = logprobs;
            return this;
        }

        public Builder finishReason(String finishReason) {
            this.finishReason = finishReason;
            return this;
        }

        public CompletionChoice build() {
            return new CompletionChoice(this);
        }
    }
}
