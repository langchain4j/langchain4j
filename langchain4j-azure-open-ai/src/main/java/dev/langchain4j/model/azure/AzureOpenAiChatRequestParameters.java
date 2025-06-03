package dev.langchain4j.model.azure;

import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.Utils.copyIfNotNull;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.Utils.quoted;

import com.azure.ai.openai.models.AzureChatEnhancementConfiguration;
import com.azure.ai.openai.models.AzureChatExtensionConfiguration;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.DefaultChatRequestParameters;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class AzureOpenAiChatRequestParameters extends DefaultChatRequestParameters {

    private final Integer maxCompletionTokens;
    private final Map<String, Integer> logitBias;
    private final Boolean parallelToolCalls;
    private final Long seed;
    private final String user;
    private final Boolean store;
    private final Map<String, String> metadata;
    private final List<AzureChatExtensionConfiguration> dataSources;
    private final AzureChatEnhancementConfiguration enhancements;

    private AzureOpenAiChatRequestParameters(Builder builder) {
        super(builder);
        this.maxCompletionTokens = builder.maxCompletionTokens;
        this.logitBias = copy(builder.logitBias);
        this.parallelToolCalls = builder.parallelToolCalls;
        this.seed = builder.seed;
        this.user = builder.user;
        this.store = builder.store;
        this.metadata = copyIfNotNull(builder.metadata);
        this.dataSources = copyIfNotNull(builder.dataSources);
        this.enhancements = enhancements();
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

    public Long seed() {
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

    public List<AzureChatExtensionConfiguration> dataSources() {
        return dataSources;
    }

    public AzureChatEnhancementConfiguration enhancements() {
        return enhancements;
    }

    @Override
    public AzureOpenAiChatRequestParameters overrideWith(ChatRequestParameters that) {
        return AzureOpenAiChatRequestParameters.builder()
                .overrideWith(this)
                .overrideWith(that)
                .build();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        AzureOpenAiChatRequestParameters that = (AzureOpenAiChatRequestParameters) o;
        return Objects.equals(maxCompletionTokens, that.maxCompletionTokens)
                && Objects.equals(logitBias, that.logitBias)
                && Objects.equals(parallelToolCalls, that.parallelToolCalls)
                && Objects.equals(seed, that.seed)
                && Objects.equals(user, that.user)
                && Objects.equals(store, that.store)
                && Objects.equals(metadata, that.metadata)
                && Objects.equals(dataSources, that.dataSources)
                && Objects.equals(enhancements, that.enhancements);
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
                dataSources,
                enhancements);
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
                + metadata + ", dataSources="
                + dataSources + ", enhancements="
                + enhancements + '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder extends DefaultChatRequestParameters.Builder<Builder> {

        private Integer maxCompletionTokens;
        private Map<String, Integer> logitBias;
        private Boolean parallelToolCalls;
        private Long seed;
        private String user;
        private Boolean store;
        private Map<String, String> metadata;
        private List<AzureChatExtensionConfiguration> dataSources;
        private AzureChatEnhancementConfiguration enhancements;

        @Override
        public Builder overrideWith(ChatRequestParameters parameters) {
            super.overrideWith(parameters);
            if (parameters instanceof AzureOpenAiChatRequestParameters azureOpenAiParameters) {
                maxCompletionTokens(getOrDefault(azureOpenAiParameters.maxCompletionTokens(), maxCompletionTokens));
                logitBias(getOrDefault(azureOpenAiParameters.logitBias(), logitBias));
                parallelToolCalls(getOrDefault(azureOpenAiParameters.parallelToolCalls(), parallelToolCalls));
                seed(getOrDefault(azureOpenAiParameters.seed(), seed));
                user(getOrDefault(azureOpenAiParameters.user(), user));
                store(getOrDefault(azureOpenAiParameters.store(), store));
                metadata(getOrDefault(azureOpenAiParameters.metadata(), metadata));
                dataSources(getOrDefault(azureOpenAiParameters.dataSources(), dataSources));
                enhancements(getOrDefault(azureOpenAiParameters.enhancements(), enhancements));
            }
            return this;
        }

        public Builder modelName(AzureOpenAiChatModelName modelName) {
            return super.modelName(modelName.toString());
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

        public Builder seed(Long seed) {
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

        public Builder dataSources(List<AzureChatExtensionConfiguration> dataSources) {
            this.dataSources = dataSources;
            return this;
        }

        public Builder enhancements(AzureChatEnhancementConfiguration enhancements) {
            this.enhancements = enhancements;
            return this;
        }

        @Override
        public AzureOpenAiChatRequestParameters build() {
            return new AzureOpenAiChatRequestParameters(this);
        }
    }
}
