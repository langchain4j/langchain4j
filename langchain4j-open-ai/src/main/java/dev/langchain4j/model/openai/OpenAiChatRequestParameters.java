package dev.langchain4j.model.openai;

import dev.langchain4j.Experimental;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.DefaultChatRequestParameters;

import java.util.Map;
import java.util.Objects;

import static dev.langchain4j.internal.Utils.copyIfNotNull;

@Experimental
public class OpenAiChatRequestParameters extends DefaultChatRequestParameters {

    private final Map<String, Integer> logitBias;
    private final Boolean parallelToolCalls;
    private final Integer seed;
    private final String user;
    private final Boolean store;
    private final Map<String, String> metadata;
    private final String serviceTier;

    private OpenAiChatRequestParameters(Builder builder) {
        super(builder);
        this.logitBias = copyIfNotNull(builder.logitBias);
        this.parallelToolCalls = builder.parallelToolCalls;
        this.seed = builder.seed;
        this.user = builder.user;
        this.store = builder.store;
        this.metadata = copyIfNotNull(builder.metadata);
        this.serviceTier = builder.serviceTier;
    }

    OpenAiChatRequestParameters(ChatRequestParameters parameters) {
        super(parameters);
        if (parameters instanceof OpenAiChatRequestParameters openAiParameters) {
            this.logitBias = copyIfNotNull(openAiParameters.logitBias);
            this.parallelToolCalls = openAiParameters.parallelToolCalls;
            this.seed = openAiParameters.seed;
            this.user = openAiParameters.user;
            this.store = openAiParameters.store;
            this.metadata = copyIfNotNull(openAiParameters.metadata);
            this.serviceTier = openAiParameters.serviceTier;
        } else {
            this.logitBias = null;
            this.parallelToolCalls = null;
            this.seed = null;
            this.user = null;
            this.store = null;
            this.metadata = null;
            this.serviceTier = null;
        }
    }

    public Map<String, Integer> logitBias() {
        return logitBias;
    }

    public Boolean parallelToolCalls() {
        return parallelToolCalls;
    }

    public Integer seed() {
        return seed;
    }

    public String user() {
        return user;
    }

    public Boolean store() {
        return store;
    }

    public Map<String, String> metadata() {
        return metadata;
    }

    public String serviceTier() {
        return serviceTier;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        OpenAiChatRequestParameters that = (OpenAiChatRequestParameters) o;
        return Objects.equals(logitBias, that.logitBias)
                && Objects.equals(parallelToolCalls, that.parallelToolCalls)
                && Objects.equals(seed, that.seed)
                && Objects.equals(user, that.user)
                && Objects.equals(store, that.store)
                && Objects.equals(metadata, that.metadata)
                && Objects.equals(serviceTier, that.serviceTier);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                super.hashCode(),
                logitBias,
                parallelToolCalls,
                seed,
                user,
                store,
                metadata,
                serviceTier
        );
    }

    @Override
    public String toString() {
        return "OpenAiChatRequestParameters{" +
                "modelName='" + modelName() + '\'' +
                ", temperature=" + temperature() +
                ", topP=" + topP() +
                ", topK=" + topK() +
                ", frequencyPenalty=" + frequencyPenalty() +
                ", presencePenalty=" + presencePenalty() +
                ", maxOutputTokens=" + maxOutputTokens() +
                ", stopSequences=" + stopSequences() +
                ", toolSpecifications=" + toolSpecifications() +
                ", toolChoice=" + toolChoice() +
                ", responseFormat=" + responseFormat() +
                ", logitBias=" + logitBias +
                ", parallelToolCalls=" + parallelToolCalls +
                ", seed=" + seed +
                ", user='" + user + '\'' +
                ", store=" + store +
                ", metadata=" + metadata +
                ", serviceTier='" + serviceTier + '\'' +
                '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder extends DefaultChatRequestParameters.Builder<Builder> {

        private Map<String, Integer> logitBias;
        private Boolean parallelToolCalls;
        private Integer seed;
        private String user;
        private Boolean store;
        private Map<String, String> metadata;
        private String serviceTier;

        public Builder modelName(OpenAiChatModelName modelName) {
            return super.modelName(modelName.toString());
        }

        public Builder logitBias(Map<String, Integer> logitBias) {
            this.logitBias = logitBias;
            return this;
        }

        public Builder parallelToolCalls(Boolean parallelToolCalls) {
            this.parallelToolCalls = parallelToolCalls;
            return this;
        }

        public Builder seed(Integer seed) {
            this.seed = seed;
            return this;
        }

        public Builder user(String user) {
            this.user = user;
            return this;
        }

        public Builder store(Boolean store) {
            this.store = store;
            return this;
        }

        public Builder metadata(Map<String, String> metadata) {
            this.metadata = metadata;
            return this;
        }

        public Builder serviceTier(String serviceTier) {
            this.serviceTier = serviceTier;
            return this;
        }

        @Override
        public OpenAiChatRequestParameters build() {
            return new OpenAiChatRequestParameters(this);
        }
    }
}
