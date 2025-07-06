package dev.langchain4j.model.openaiofficial;

import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.Utils.getOrDefault;

import com.openai.models.ChatModel;
import dev.langchain4j.Experimental;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.DefaultChatRequestParameters;
import java.util.Map;
import java.util.Objects;

@Experimental
public class OpenAiOfficialChatRequestParameters extends DefaultChatRequestParameters {

    public static final OpenAiOfficialChatRequestParameters EMPTY = OpenAiOfficialChatRequestParameters.builder().build();

    private final Integer maxCompletionTokens;
    private final Map<String, Integer> logitBias;
    private final Boolean parallelToolCalls;
    private final Integer seed;
    private final String user;
    private final Boolean store;
    private final Map<String, String> metadata;
    private final String serviceTier;
    private final String reasoningEffort;

    private OpenAiOfficialChatRequestParameters(Builder builder) {
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

    @Override
    public OpenAiOfficialChatRequestParameters overrideWith(ChatRequestParameters that) {
        return OpenAiOfficialChatRequestParameters.builder()
                .overrideWith(this)
                .overrideWith(that)
                .build();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        OpenAiOfficialChatRequestParameters that = (OpenAiOfficialChatRequestParameters) o;
        return Objects.equals(maxCompletionTokens, that.maxCompletionTokens)
                && Objects.equals(logitBias, that.logitBias)
                && Objects.equals(parallelToolCalls, that.parallelToolCalls)
                && Objects.equals(seed, that.seed)
                && Objects.equals(user, that.user)
                && Objects.equals(store, that.store)
                && Objects.equals(metadata, that.metadata)
                && Objects.equals(serviceTier, that.serviceTier)
                && Objects.equals(reasoningEffort, that.reasoningEffort);
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
                reasoningEffort);
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

        @Override
        public Builder overrideWith(ChatRequestParameters parameters) {
            super.overrideWith(parameters);
            if (parameters instanceof OpenAiOfficialChatRequestParameters openAiParameters) {
                maxCompletionTokens(getOrDefault(openAiParameters.maxCompletionTokens(), maxCompletionTokens));
                logitBias(getOrDefault(openAiParameters.logitBias(), logitBias));
                parallelToolCalls(getOrDefault(openAiParameters.parallelToolCalls(), parallelToolCalls));
                seed(getOrDefault(openAiParameters.seed(), seed));
                user(getOrDefault(openAiParameters.user(), user));
                store(getOrDefault(openAiParameters.store(), store));
                metadata(getOrDefault(openAiParameters.metadata(), metadata));
                serviceTier(getOrDefault(openAiParameters.serviceTier(), serviceTier));
                reasoningEffort(getOrDefault(openAiParameters.reasoningEffort(), reasoningEffort));
            }
            return this;
        }

        public Builder modelName(ChatModel modelName) {
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

        @Override
        public OpenAiOfficialChatRequestParameters build() {
            return new OpenAiOfficialChatRequestParameters(this);
        }
    }
}
