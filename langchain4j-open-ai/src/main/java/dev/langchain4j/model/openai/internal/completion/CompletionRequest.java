package dev.langchain4j.model.openai.internal.completion;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import dev.langchain4j.model.openai.internal.shared.StreamOptions;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import static java.util.Collections.unmodifiableList;
import static java.util.Collections.unmodifiableMap;

@JsonDeserialize(builder = CompletionRequest.Builder.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public final class CompletionRequest {

    @JsonProperty
    private final String model;
    @JsonProperty
    private final String prompt;
    @JsonProperty
    private final String suffix;
    @JsonProperty
    private final Integer maxTokens;
    @JsonProperty
    private final Double temperature;
    @JsonProperty
    private final Double topP;
    @JsonProperty
    private final Integer n;
    @JsonProperty
    private final Boolean stream;
    @JsonProperty
    private final StreamOptions streamOptions;
    @JsonProperty
    private final Integer logprobs;
    @JsonProperty
    private final Boolean echo;
    @JsonProperty
    private final List<String> stop;
    @JsonProperty
    private final Double presencePenalty;
    @JsonProperty
    private final Double frequencyPenalty;
    @JsonProperty
    private final Integer bestOf;
    @JsonProperty
    private final Map<String, Integer> logitBias;
    @JsonProperty
    private final String user;

    public CompletionRequest(Builder builder) {
        this.model = builder.model;
        this.prompt = builder.prompt;
        this.suffix = builder.suffix;
        this.maxTokens = builder.maxTokens;
        this.temperature = builder.temperature;
        this.topP = builder.topP;
        this.n = builder.n;
        this.stream = builder.stream;
        this.streamOptions = builder.streamOptions;
        this.logprobs = builder.logprobs;
        this.echo = builder.echo;
        this.stop = builder.stop;
        this.presencePenalty = builder.presencePenalty;
        this.frequencyPenalty = builder.frequencyPenalty;
        this.bestOf = builder.bestOf;
        this.logitBias = builder.logitBias;
        this.user = builder.user;
    }

    public String model() {
        return model;
    }

    public String prompt() {
        return prompt;
    }

    public String suffix() {
        return suffix;
    }

    public Integer maxTokens() {
        return maxTokens;
    }

    public Double temperature() {
        return temperature;
    }

    public Double topP() {
        return topP;
    }

    public Integer n() {
        return n;
    }

    public Boolean stream() {
        return stream;
    }

    public StreamOptions streamOptions() {
        return streamOptions;
    }

    public Integer logprobs() {
        return logprobs;
    }

    public Boolean echo() {
        return echo;
    }

    public List<String> stop() {
        return stop;
    }

    public Double presencePenalty() {
        return presencePenalty;
    }

    public Double frequencyPenalty() {
        return frequencyPenalty;
    }

    public Integer bestOf() {
        return bestOf;
    }

    public Map<String, Integer> logitBias() {
        return logitBias;
    }

    public String user() {
        return user;
    }

    @Override
    public boolean equals(Object another) {
        if (this == another) return true;
        return another instanceof CompletionRequest
                && equalTo((CompletionRequest) another);
    }

    private boolean equalTo(CompletionRequest another) {
        return Objects.equals(model, another.model)
                && Objects.equals(prompt, another.prompt)
                && Objects.equals(suffix, another.suffix)
                && Objects.equals(maxTokens, another.maxTokens)
                && Objects.equals(temperature, another.temperature)
                && Objects.equals(topP, another.topP)
                && Objects.equals(n, another.n)
                && Objects.equals(stream, another.stream)
                && Objects.equals(streamOptions, another.streamOptions)
                && Objects.equals(logprobs, another.logprobs)
                && Objects.equals(echo, another.echo)
                && Objects.equals(stop, another.stop)
                && Objects.equals(presencePenalty, another.presencePenalty)
                && Objects.equals(frequencyPenalty, another.frequencyPenalty)
                && Objects.equals(bestOf, another.bestOf)
                && Objects.equals(logitBias, another.logitBias)
                && Objects.equals(user, another.user);
    }

    @Override
    public int hashCode() {
        int h = 5381;
        h += (h << 5) + Objects.hashCode(model);
        h += (h << 5) + Objects.hashCode(prompt);
        h += (h << 5) + Objects.hashCode(suffix);
        h += (h << 5) + Objects.hashCode(maxTokens);
        h += (h << 5) + Objects.hashCode(temperature);
        h += (h << 5) + Objects.hashCode(topP);
        h += (h << 5) + Objects.hashCode(n);
        h += (h << 5) + Objects.hashCode(stream);
        h += (h << 5) + Objects.hashCode(streamOptions);
        h += (h << 5) + Objects.hashCode(logprobs);
        h += (h << 5) + Objects.hashCode(echo);
        h += (h << 5) + Objects.hashCode(stop);
        h += (h << 5) + Objects.hashCode(presencePenalty);
        h += (h << 5) + Objects.hashCode(frequencyPenalty);
        h += (h << 5) + Objects.hashCode(bestOf);
        h += (h << 5) + Objects.hashCode(logitBias);
        h += (h << 5) + Objects.hashCode(user);
        return h;
    }

    @Override
    public String toString() {
        return "CompletionRequest{"
                + "model=" + model
                + ", prompt=" + prompt
                + ", suffix=" + suffix
                + ", maxTokens=" + maxTokens
                + ", temperature=" + temperature
                + ", topP=" + topP
                + ", n=" + n
                + ", stream=" + stream
                + ", streamOptions=" + streamOptions
                + ", logprobs=" + logprobs
                + ", echo=" + echo
                + ", stop=" + stop
                + ", presencePenalty=" + presencePenalty
                + ", frequencyPenalty=" + frequencyPenalty
                + ", bestOf=" + bestOf
                + ", logitBias=" + logitBias
                + ", user=" + user
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
        private String prompt;
        private String suffix;
        private Integer maxTokens;
        private Double temperature;
        private Double topP;
        private Integer n;
        private Boolean stream;
        private StreamOptions streamOptions;
        private Integer logprobs;
        private Boolean echo;
        private List<String> stop;
        private Double presencePenalty;
        private Double frequencyPenalty;
        private Integer bestOf;
        private Map<String, Integer> logitBias;
        private String user;

        public Builder from(CompletionRequest request) {
            model(request.model);
            prompt(request.prompt);
            suffix(request.suffix);
            maxTokens(request.maxTokens);
            temperature(request.temperature);
            topP(request.topP);
            n(request.n);
            stream(request.stream);
            streamOptions(request.streamOptions);
            logprobs(request.logprobs);
            echo(request.echo);
            stop(request.stop);
            presencePenalty(request.presencePenalty);
            frequencyPenalty(request.frequencyPenalty);
            bestOf(request.bestOf);
            logitBias(request.logitBias);
            user(request.user);
            return this;
        }

        public Builder model(String model) {
            this.model = model;
            return this;
        }

        public Builder prompt(String prompt) {
            this.prompt = prompt;
            return this;
        }

        public Builder suffix(String suffix) {
            this.suffix = suffix;
            return this;
        }

        public Builder maxTokens(Integer maxTokens) {
            this.maxTokens = maxTokens;
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

        public Builder n(Integer n) {
            this.n = n;
            return this;
        }

        public Builder stream(Boolean stream) {
            this.stream = stream;
            return this;
        }

        public Builder streamOptions(StreamOptions streamOptions) {
            this.streamOptions = streamOptions;
            return this;
        }

        public Builder logprobs(Integer logprobs) {
            this.logprobs = logprobs;
            return this;
        }

        public Builder echo(Boolean echo) {
            this.echo = echo;
            return this;
        }

        public Builder stop(List<String> stop) {
            if (stop != null) {
                this.stop = unmodifiableList(stop);
            }
            return this;
        }

        public Builder presencePenalty(Double presencePenalty) {
            this.presencePenalty = presencePenalty;
            return this;
        }

        public Builder frequencyPenalty(Double frequencyPenalty) {
            this.frequencyPenalty = frequencyPenalty;
            return this;
        }

        public Builder bestOf(Integer bestOf) {
            this.bestOf = bestOf;
            return this;
        }

        public Builder logitBias(Map<String, Integer> logitBias) {
            if (logitBias != null) {
                this.logitBias = unmodifiableMap(logitBias);
            }
            return this;
        }

        public Builder user(String user) {
            this.user = user;
            return this;
        }

        public CompletionRequest build() {
            return new CompletionRequest(this);
        }
    }
}
