package dev.langchain4j.model.openai;

import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.Utils.quoted;

import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.DefaultChatRequestParameters;
import java.util.Map;
import java.util.Objects;

public class OpenAiChatRequestParameters extends DefaultChatRequestParameters {

    public static final OpenAiChatRequestParameters EMPTY =
            OpenAiChatRequestParameters.builder().build();

    private final Integer maxCompletionTokens;
    private final Map<String, Integer> logitBias;
    private final Boolean parallelToolCalls;
    private final Integer seed;
    private final String user;
    private final Boolean store;
    private final Map<String, String> metadata;
    private final String serviceTier;
    private final String reasoningEffort;
    private final Map<String, Object> customParameters;

    private OpenAiChatRequestParameters(Builder builder) {
        super(builder);
        this.maxCompletionTokens = builder.maxCompletionTokens;
        this.logitBias = copy(builder.logitBias);
        this.parallelToolCalls = builder.parallelToolCalls;
        this.seed = builder.seed;
        this.user = builder.user;
        this.store = builder.store;
        this.metadata = copy(builder.metadata);
        this.serviceTier = builder.serviceTier;
        this.reasoningEffort = builder.reasoningEffort;
        this.customParameters = copy(builder.customParameters);
    }

    public Integer maxCompletionTokens() {
        return maxCompletionTokens;
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

    public String reasoningEffort() {
        return reasoningEffort;
    }

    public Map<String, Object> customParameters() {
        return customParameters;
    }

    @Override
    public OpenAiChatRequestParameters overrideWith(ChatRequestParameters that) {
        return OpenAiChatRequestParameters.builder()
                .overrideWith(this)
                .overrideWith(that)
                .build();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        OpenAiChatRequestParameters that = (OpenAiChatRequestParameters) o;
        return Objects.equals(maxCompletionTokens, that.maxCompletionTokens)
                && Objects.equals(logitBias, that.logitBias)
                && Objects.equals(parallelToolCalls, that.parallelToolCalls)
                && Objects.equals(seed, that.seed)
                && Objects.equals(user, that.user)
                && Objects.equals(store, that.store)
                && Objects.equals(metadata, that.metadata)
                && Objects.equals(serviceTier, that.serviceTier)
                && Objects.equals(reasoningEffort, that.reasoningEffort)
                && Objects.equals(customParameters, that.customParameters);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                super.hashCode(),
                maxCompletionTokens,
                logitBias,
                parallelToolCalls,
                seed,
                user,
                store,
                metadata,
                serviceTier,
                reasoningEffort,
                customParameters);
    }

    @Override
    public String toString() {
        return "OpenAiChatRequestParameters{" + "modelName="
                + quoted(modelName()) + ", temperature="
                + temperature() + ", topP="
                + topP() + ", topK="
                + topK() + ", frequencyPenalty="
                + frequencyPenalty() + ", presencePenalty="
                + presencePenalty() + ", maxOutputTokens="
                + maxOutputTokens() + ", stopSequences="
                + stopSequences() + ", toolSpecifications="
                + toolSpecifications() + ", toolChoice="
                + toolChoice() + ", responseFormat="
                + responseFormat() + ", maxCompletionTokens="
                + maxCompletionTokens + ", logitBias="
                + logitBias + ", parallelToolCalls="
                + parallelToolCalls + ", seed="
                + seed + ", user="
                + quoted(user) + ", store="
                + store + ", metadata="
                + metadata + ", serviceTier="
                + quoted(serviceTier) + ", reasoningEffort="
                + quoted(reasoningEffort) + ", customParameters="
                + customParameters + '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder extends DefaultChatRequestParameters.Builder<Builder> {

        private Integer maxCompletionTokens;
        private Map<String, Integer> logitBias;
        private Boolean parallelToolCalls;
        private Integer seed;
        private String user;
        private Boolean store;
        private Map<String, String> metadata;
        private String serviceTier;
        private String reasoningEffort;
        private Map<String, Object> customParameters;

        @Override
        public Builder overrideWith(ChatRequestParameters parameters) {
            super.overrideWith(parameters);
            if (parameters instanceof OpenAiChatRequestParameters openAiParameters) {
                maxCompletionTokens(getOrDefault(openAiParameters.maxCompletionTokens(), maxCompletionTokens));
                logitBias(getOrDefault(openAiParameters.logitBias(), logitBias));
                parallelToolCalls(getOrDefault(openAiParameters.parallelToolCalls(), parallelToolCalls));
                seed(getOrDefault(openAiParameters.seed(), seed));
                user(getOrDefault(openAiParameters.user(), user));
                store(getOrDefault(openAiParameters.store(), store));
                metadata(getOrDefault(openAiParameters.metadata(), metadata));
                serviceTier(getOrDefault(openAiParameters.serviceTier(), serviceTier));
                reasoningEffort(getOrDefault(openAiParameters.reasoningEffort(), reasoningEffort));
                customParameters(getOrDefault(openAiParameters.customParameters(), customParameters));
            }
            return this;
        }

        public Builder modelName(OpenAiChatModelName modelName) {
            return super.modelName(modelName == null ? null : modelName.toString());
        }

        public Builder maxCompletionTokens(Integer maxCompletionTokens) {
            this.maxCompletionTokens = maxCompletionTokens;
            return this;
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

        public Builder reasoningEffort(String reasoningEffort) {
            this.reasoningEffort = reasoningEffort;
            return this;
        }

        public Builder customParameters(Map<String, Object> customParameters) {
            this.customParameters = customParameters;
            return this;
        }

        @Override
        public OpenAiChatRequestParameters build() {
            return new OpenAiChatRequestParameters(this);
        }
    }
}
