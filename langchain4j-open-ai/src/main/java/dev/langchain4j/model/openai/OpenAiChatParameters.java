package dev.langchain4j.model.openai;

import dev.langchain4j.Experimental;
import dev.langchain4j.model.chat.request.ChatParameters;
import dev.langchain4j.model.chat.request.DefaultChatParameters;

import java.util.Map;
import java.util.Objects;

import static dev.langchain4j.internal.Utils.copyIfNotNull;

@Experimental
public class OpenAiChatParameters extends DefaultChatParameters {

    private final Map<String, Integer> logitBias;
    private final Boolean parallelToolCalls;
    private final Integer seed;
    private final String user;
    private final Boolean store;
    private final Map<String, String> metadata;
    private final String serviceTier;
    // TODO max_completion_tokens?

    private OpenAiChatParameters(Builder builder) { // TODO visibility
        super(builder);
        this.logitBias = copyIfNotNull(builder.logitBias);
        this.parallelToolCalls = builder.parallelToolCalls;
        this.seed = builder.seed;
        this.user = builder.user;
        this.store = builder.store;
        this.metadata = copyIfNotNull(builder.metadata);
        this.serviceTier = builder.serviceTier;
    }

    protected OpenAiChatParameters(ChatParameters chatParameters) { // TODO visibility
        super(chatParameters);
        if (chatParameters instanceof OpenAiChatParameters openAiChatParameters) { // TODO is this needed?
            this.logitBias = copyIfNotNull(openAiChatParameters.logitBias);
            this.parallelToolCalls = openAiChatParameters.parallelToolCalls;
            this.seed = openAiChatParameters.seed;
            this.user = openAiChatParameters.user;
            this.store = openAiChatParameters.store;
            this.metadata = openAiChatParameters.metadata;
            this.serviceTier = openAiChatParameters.serviceTier;
        } else { // TODO is this needed?
            this.logitBias = null;
            this.parallelToolCalls = null;
            this.seed = null;
            this.user = null;
            this.store = null;
            this.metadata = null;
            this.serviceTier = null;
        }
    }

    @Experimental
    public Map<String, Integer> logitBias() {
        return logitBias;
    }

    @Experimental
    public Boolean parallelToolCalls() {
        return parallelToolCalls;
    }

    @Experimental
    public Integer seed() {
        return seed;
    }

    @Experimental
    public String user() {
        return user;
    }

    @Experimental
    public Boolean store() {
        return store;
    }

    @Experimental
    public Map<String, String> metadata() {
        return metadata;
    }

    @Experimental
    public String serviceTier() {
        return serviceTier;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        OpenAiChatParameters that = (OpenAiChatParameters) o;
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
        // TODO inherited
        return "OpenAiChatParameters{" +
                "logitBias=" + logitBias +
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

    public static class Builder extends DefaultChatParameters.Builder<Builder> {

        private Map<String, Integer> logitBias;
        private Boolean parallelToolCalls;
        private Integer seed;
        private String user;
        private Boolean store;
        private Map<String, String> metadata;
        private String serviceTier;

        @Experimental
        public Builder modelName(OpenAiChatModelName modelName) {
            return super.modelName(modelName.toString());
        }

        @Experimental
        public Builder logitBias(Map<String, Integer> logitBias) {
            this.logitBias = logitBias;
            return this;
        }

        @Experimental
        public Builder parallelToolCalls(Boolean parallelToolCalls) {
            this.parallelToolCalls = parallelToolCalls;
            return this;
        }

        @Experimental
        public Builder seed(Integer seed) {
            this.seed = seed;
            return this;
        }

        @Experimental
        public Builder user(String user) {
            this.user = user;
            return this;
        }

        @Experimental
        public Builder store(Boolean store) {
            this.store = store;
            return this;
        }

        @Experimental
        public Builder metadata(Map<String, String> metadata) {
            this.metadata = metadata;
            return this;
        }

        @Experimental
        public Builder serviceTier(String serviceTier) {
            this.serviceTier = serviceTier;
            return this;
        }

        @Override
        public OpenAiChatParameters build() {
            return new OpenAiChatParameters(this);
        }
    }
}
