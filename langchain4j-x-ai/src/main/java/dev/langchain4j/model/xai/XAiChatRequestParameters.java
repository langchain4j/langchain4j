package dev.langchain4j.model.xai;

import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.Utils.getOrDefault;

import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.DefaultChatRequestParameters;
import java.util.List;
import java.util.Objects;

/**
 * xAI-specific parameters of the xAI Responses API, on top of the common {@link ChatRequestParameters}.
 */
public class XAiChatRequestParameters extends DefaultChatRequestParameters {

    public static final XAiChatRequestParameters EMPTY =
            XAiChatRequestParameters.builder().build();

    private final String previousResponseId;
    private final Boolean store;
    private final String reasoningEffort;
    private final List<String> include;
    private final Integer maxToolCalls;
    private final Boolean parallelToolCalls;
    private final String promptCacheKey;
    private final String user;

    private XAiChatRequestParameters(Builder builder) {
        super(builder);
        this.previousResponseId = builder.previousResponseId;
        this.store = builder.store;
        this.reasoningEffort = builder.reasoningEffort;
        this.include = copy(builder.include);
        this.maxToolCalls = builder.maxToolCalls;
        this.parallelToolCalls = builder.parallelToolCalls;
        this.promptCacheKey = builder.promptCacheKey;
        this.user = builder.user;
    }

    public String previousResponseId() {
        return previousResponseId;
    }

    public Boolean store() {
        return store;
    }

    public String reasoningEffort() {
        return reasoningEffort;
    }

    public List<String> include() {
        return include;
    }

    public Integer maxToolCalls() {
        return maxToolCalls;
    }

    public Boolean parallelToolCalls() {
        return parallelToolCalls;
    }

    public String promptCacheKey() {
        return promptCacheKey;
    }

    public String user() {
        return user;
    }

    @Override
    public XAiChatRequestParameters overrideWith(ChatRequestParameters that) {
        return XAiChatRequestParameters.builder()
                .overrideWith(this)
                .overrideWith(that)
                .build();
    }

    @Override
    public XAiChatRequestParameters defaultedBy(ChatRequestParameters that) {
        return XAiChatRequestParameters.builder()
                .overrideWith(that)
                .overrideWith(this)
                .build();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        XAiChatRequestParameters that = (XAiChatRequestParameters) o;
        return Objects.equals(previousResponseId, that.previousResponseId)
                && Objects.equals(store, that.store)
                && Objects.equals(reasoningEffort, that.reasoningEffort)
                && Objects.equals(include, that.include)
                && Objects.equals(maxToolCalls, that.maxToolCalls)
                && Objects.equals(parallelToolCalls, that.parallelToolCalls)
                && Objects.equals(promptCacheKey, that.promptCacheKey)
                && Objects.equals(user, that.user);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                super.hashCode(),
                previousResponseId,
                store,
                reasoningEffort,
                include,
                maxToolCalls,
                parallelToolCalls,
                promptCacheKey,
                user);
    }

    @Override
    public String toString() {
        return "XAiChatRequestParameters{" + "modelName='"
                + modelName() + '\'' + ", temperature="
                + temperature() + ", topP="
                + topP() + ", topK="
                + topK() + ", maxOutputTokens="
                + maxOutputTokens() + ", toolSpecifications="
                + toolSpecifications() + ", toolChoice="
                + toolChoice() + ", responseFormat="
                + responseFormat() + ", previousResponseId='"
                + previousResponseId + '\'' + ", store="
                + store + ", reasoningEffort='"
                + reasoningEffort + '\'' + ", include="
                + include + ", maxToolCalls="
                + maxToolCalls + ", parallelToolCalls="
                + parallelToolCalls + ", promptCacheKey='"
                + promptCacheKey + '\'' + ", user='"
                + user + '\'' + '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder extends DefaultChatRequestParameters.Builder<Builder> {

        private String previousResponseId;
        private Boolean store;
        private String reasoningEffort;
        private List<String> include;
        private Integer maxToolCalls;
        private Boolean parallelToolCalls;
        private String promptCacheKey;
        private String user;

        @Override
        public Builder overrideWith(ChatRequestParameters parameters) {
            super.overrideWith(parameters);
            if (parameters instanceof XAiChatRequestParameters p) {
                previousResponseId(getOrDefault(p.previousResponseId(), previousResponseId));
                store(getOrDefault(p.store(), store));
                reasoningEffort(getOrDefault(p.reasoningEffort(), reasoningEffort));
                include(getOrDefault(p.include(), include));
                maxToolCalls(getOrDefault(p.maxToolCalls(), maxToolCalls));
                parallelToolCalls(getOrDefault(p.parallelToolCalls(), parallelToolCalls));
                promptCacheKey(getOrDefault(p.promptCacheKey(), promptCacheKey));
                user(getOrDefault(p.user(), user));
            }
            return this;
        }

        public Builder modelName(XAiChatModelName modelName) {
            return super.modelName(modelName == null ? null : modelName.toString());
        }

        /**
         * Continues the conversation stored on the xAI side under the given response ID.
         */
        public Builder previousResponseId(String previousResponseId) {
            this.previousResponseId = previousResponseId;
            return this;
        }

        /**
         * Whether xAI should store the request and response (kept for 30 days).
         */
        public Builder store(Boolean store) {
            this.store = store;
            return this;
        }

        /**
         * Reasoning effort, e.g. "low" or "high". Supported values depend on the model.
         */
        public Builder reasoningEffort(String reasoningEffort) {
            this.reasoningEffort = reasoningEffort;
            return this;
        }

        /**
         * Additional output to include, e.g. "reasoning.encrypted_content".
         */
        public Builder include(List<String> include) {
            this.include = include;
            return this;
        }

        public Builder maxToolCalls(Integer maxToolCalls) {
            this.maxToolCalls = maxToolCalls;
            return this;
        }

        public Builder parallelToolCalls(Boolean parallelToolCalls) {
            this.parallelToolCalls = parallelToolCalls;
            return this;
        }

        public Builder promptCacheKey(String promptCacheKey) {
            this.promptCacheKey = promptCacheKey;
            return this;
        }

        public Builder user(String user) {
            this.user = user;
            return this;
        }

        @Override
        public XAiChatRequestParameters build() {
            return new XAiChatRequestParameters(this);
        }
    }
}
