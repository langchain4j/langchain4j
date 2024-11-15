package dev.langchain4j.model.watsonx.internal.api.requests;

public class WatsonxChatParameters {

    public record TextChatResponseFormat(String type) {}

    private final Double frequencyPenalty;
    private final Boolean logprobs;
    private final Integer topLogprobs;
    private final Integer maxTokens;
    private final Integer n;
    private final Double presencePenalty;
    private final Double temperature;
    private final Double topP;
    private final Long timeLimit;
    private final TextChatResponseFormat responseFormat;

    public WatsonxChatParameters(Builder builder) {
        this.frequencyPenalty = builder.frequencyPenalty;
        this.logprobs = builder.logprobs;
        this.topLogprobs = builder.topLogprobs;
        this.maxTokens = builder.maxTokens;
        this.n = builder.n;
        this.presencePenalty = builder.presencePenalty;
        this.temperature = builder.temperature;
        this.topP = builder.topP;
        this.timeLimit = builder.timeLimit;

        if (builder.responseFormat != null)
            this.responseFormat = new TextChatResponseFormat(builder.responseFormat);
        else
            this.responseFormat = null;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Double getFrequencyPenalty() {
        return frequencyPenalty;
    }

    public Boolean getLogprobs() {
        return logprobs;
    }

    public Integer getTopLogprobs() {
        return topLogprobs;
    }

    public Integer getMaxTokens() {
        return maxTokens;
    }

    public Integer getN() {
        return n;
    }

    public Double getPresencePenalty() {
        return presencePenalty;
    }

    public Double getTemperature() {
        return temperature;
    }

    public Double getTopP() {
        return topP;
    }

    public Long getTimeLimit() {
        return timeLimit;
    }

    public TextChatResponseFormat getResponseFormat() {
        return responseFormat;
    }

    public static class Builder {

        private Double frequencyPenalty;
        private Boolean logprobs;
        private Integer topLogprobs;
        private Integer maxTokens;
        private Integer n;
        private Double presencePenalty;
        private Double temperature;
        private Double topP;
        private Long timeLimit;
        private String responseFormat;

        public Builder frequencyPenalty(Double frequencyPenalty) {
            this.frequencyPenalty = frequencyPenalty;
            return this;
        }

        public Builder logprobs(Boolean logprobs) {
            this.logprobs = logprobs;
            return this;
        }

        public Builder topLogprobs(Integer topLogprobs) {
            this.topLogprobs = topLogprobs;
            return this;
        }

        public Builder maxTokens(Integer maxTokens) {
            this.maxTokens = maxTokens;
            return this;
        }

        public Builder n(Integer n) {
            this.n = n;
            return this;
        }

        public Builder presencePenalty(Double presencePenalty) {
            this.presencePenalty = presencePenalty;
            return this;
        }

        public Builder temperature(Double temperature) {
            this.temperature = temperature;
            return this;
        }

        public Builder topP(Double topP) {
            this.topP = topP;
            return this;
        }

        public Builder timeLimit(Long timeLimit) {
            this.timeLimit = timeLimit;
            return this;
        }

        public Builder responseFormat(String responseFormat) {
            this.responseFormat = responseFormat;
            return this;
        }

        public WatsonxChatParameters build() {
            return new WatsonxChatParameters(this);
        }
    }
}
