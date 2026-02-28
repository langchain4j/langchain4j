package dev.langchain4j.model.openai.internal.chat;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import dev.langchain4j.internal.JacocoIgnoreCoverageGenerated;
import java.util.Objects;

@JsonDeserialize(builder = ChatCompletionChoice.Builder.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public final class ChatCompletionChoice {

    @JsonProperty
    private final Integer index;

    @JsonProperty
    private final AssistantMessage message;

    @JsonProperty
    private final Delta delta;

    @JsonProperty
    private final String finishReason;

    @JsonProperty
    private final LogProbs logprobs;

    public ChatCompletionChoice(Builder builder) {
        this.index = builder.index;
        this.message = builder.message;
        this.delta = builder.delta;
        this.finishReason = builder.finishReason;
        this.logprobs = builder.logprobs;
    }

    public Integer index() {
        return index;
    }

    public AssistantMessage message() {
        return message;
    }

    public Delta delta() {
        return delta;
    }

    public String finishReason() {
        return finishReason;
    }

    public LogProbs logprobs() {
        return logprobs;
    }

    @Override
    @JacocoIgnoreCoverageGenerated
    public boolean equals(Object another) {
        if (this == another) return true;
        return another instanceof ChatCompletionChoice && equalTo((ChatCompletionChoice) another);
    }

    @JacocoIgnoreCoverageGenerated
    private boolean equalTo(ChatCompletionChoice another) {
        return Objects.equals(index, another.index)
                && Objects.equals(message, another.message)
                && Objects.equals(delta, another.delta)
                && Objects.equals(finishReason, another.finishReason)
                && Objects.equals(logprobs, another.logprobs);
    }

    @Override
    @JacocoIgnoreCoverageGenerated
    public int hashCode() {
        int h = 5381;
        h += (h << 5) + Objects.hashCode(index);
        h += (h << 5) + Objects.hashCode(message);
        h += (h << 5) + Objects.hashCode(delta);
        h += (h << 5) + Objects.hashCode(finishReason);
        h += (h << 5) + Objects.hashCode(logprobs);
        return h;
    }

    @Override
    @JacocoIgnoreCoverageGenerated
    public String toString() {
        return "ChatCompletionChoice{"
                + "index=" + index
                + ", message=" + message
                + ", delta=" + delta
                + ", finishReason=" + finishReason
                + ", logprobs=" + logprobs
                + "}";
    }

    public static Builder builder() {
        return new Builder();
    }

    @JsonPOJOBuilder(withPrefix = "")
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static final class Builder {

        private Integer index;
        private AssistantMessage message;
        private Delta delta;
        private String finishReason;
        private LogProbs logprobs;

        public Builder index(Integer index) {
            this.index = index;
            return this;
        }

        public Builder message(AssistantMessage message) {
            this.message = message;
            return this;
        }

        public Builder delta(Delta delta) {
            this.delta = delta;
            return this;
        }

        public Builder finishReason(String finishReason) {
            this.finishReason = finishReason;
            return this;
        }

        public Builder logprobs(LogProbs logprobs) {
            this.logprobs = logprobs;
            return this;
        }

        public ChatCompletionChoice build() {
            return new ChatCompletionChoice(this);
        }
    }
}
