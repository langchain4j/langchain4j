package dev.langchain4j.model.chat.request;

import dev.langchain4j.Experimental;

import java.util.List;
import java.util.Objects;

import static dev.langchain4j.internal.Utils.copyIfNotNull;

/**
 * TODO
 */
@Experimental
public class ChatParameters {
    // TODO name
    // TODO place

    private final Double temperature;
    private final Double topP;
    private final Integer topK;
    private final Double frequencyPenalty;
    private final Double presencePenalty;
    private final Integer maxOutputTokens;
    private final List<String> stopSequences;

    private ChatParameters(ChatParameters.Builder builder) {
        this.temperature = builder.temperature;
        this.topP = builder.topP;
        this.topK = builder.topK;
        this.frequencyPenalty = builder.frequencyPenalty;
        this.presencePenalty = builder.presencePenalty;
        this.maxOutputTokens = builder.maxOutputTokens;
        this.stopSequences = copyIfNotNull(builder.stopSequences);
    }

    public Double temperature() {
        return temperature;
    }

    public Double topP() {
        return topP;
    }

    public Integer topK() {
        return topK;
    }

    public Double frequencyPenalty() {
        return frequencyPenalty;
    }

    public Double presencePenalty() {
        return presencePenalty;
    }

    public Integer maxOutputTokens() {
        return maxOutputTokens;
    }

    public List<String> stopSequences() {
        return stopSequences;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ChatParameters that = (ChatParameters) o;
        return Objects.equals(this.temperature, that.temperature)
                && Objects.equals(this.topP, that.topP)
                && Objects.equals(this.topK, that.topK)
                && Objects.equals(this.frequencyPenalty, that.frequencyPenalty)
                && Objects.equals(this.presencePenalty, that.presencePenalty)
                && Objects.equals(this.maxOutputTokens, that.maxOutputTokens)
                && Objects.equals(this.stopSequences, that.stopSequences);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                temperature,
                topP,
                topK,
                frequencyPenalty,
                presencePenalty,
                maxOutputTokens,
                stopSequences
        );
    }

    @Override
    public String toString() {
        return "ChatParameters {" +
                " temperature = " + temperature +
                ", topP = " + topP +
                ", topK = " + topK +
                ", frequencyPenalty = " + frequencyPenalty +
                ", presencePenalty = " + presencePenalty +
                ", maxOutputTokens = " + maxOutputTokens +
                ", stopSequences = " + stopSequences +
                " }";
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private Double temperature;
        private Double topP;
        private Integer topK;
        private Double frequencyPenalty;
        private Double presencePenalty;
        private Integer maxOutputTokens;
        private List<String> stopSequences;

        public Builder temperature(Double temperature) {
            this.temperature = temperature;
            return this;
        }

        public Builder topP(Double topP) {
            this.topP = topP;
            return this;
        }

        public Builder topK(Integer topK) {
            this.topK = topK;
            return this;
        }

        public Builder frequencyPenalty(Double frequencyPenalty) {
            this.frequencyPenalty = frequencyPenalty;
            return this;
        }

        public Builder presencePenalty(Double presencePenalty) {
            this.presencePenalty = presencePenalty;
            return this;
        }

        public Builder maxOutputTokens(Integer maxOutputTokens) {
            this.maxOutputTokens = maxOutputTokens;
            return this;
        }

        public Builder stopSequences(List<String> stopSequences) {
            this.stopSequences = stopSequences;
            return this;
        }

        public ChatParameters build() {
            return new ChatParameters(this);
        }
    }
}
