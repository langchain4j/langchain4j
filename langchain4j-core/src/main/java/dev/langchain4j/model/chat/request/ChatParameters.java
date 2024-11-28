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

    protected ChatParameters(ChatParameters.Builder builder) {
        // TODO validate params? ranges?
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

    public static Builder builder() { // TODO
        return new Builder();
    }

    public static class Builder<T extends Builder<T>> {

        private Double temperature;
        private Double topP;
        private Integer topK;
        private Double frequencyPenalty;
        private Double presencePenalty;
        private Integer maxOutputTokens;
        private List<String> stopSequences;

        public T temperature(Double temperature) {
            this.temperature = temperature;
            return (T) this;
        }

        public T topP(Double topP) {
            this.topP = topP;
            return (T) this;
        }

        public T topK(Integer topK) {
            this.topK = topK;
            return (T) this;
        }

        public T frequencyPenalty(Double frequencyPenalty) {
            this.frequencyPenalty = frequencyPenalty;
            return (T) this;
        }

        public T presencePenalty(Double presencePenalty) {
            this.presencePenalty = presencePenalty;
            return (T) this;
        }

        public T maxOutputTokens(Integer maxOutputTokens) {
            this.maxOutputTokens = maxOutputTokens;
            return (T) this;
        }

        public T stopSequences(List<String> stopSequences) {
            this.stopSequences = stopSequences;
            return (T) this;
        }

        public ChatParameters build() {
            return new ChatParameters(this);
        }
    }
}
