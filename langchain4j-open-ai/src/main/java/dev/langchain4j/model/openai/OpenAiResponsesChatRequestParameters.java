package dev.langchain4j.model.openai;

import static dev.langchain4j.internal.Utils.copyIfNotNull;
import static dev.langchain4j.internal.Utils.getOrDefault;

import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.DefaultChatRequestParameters;
import java.util.List;
import java.util.Objects;

public class OpenAiResponsesChatRequestParameters extends DefaultChatRequestParameters {

    public static final OpenAiResponsesChatRequestParameters EMPTY =
            OpenAiResponsesChatRequestParameters.builder().build();

    private final String previousResponseId;
    private final Integer maxToolCalls;
    private final Boolean parallelToolCalls;
    private final Integer topLogprobs;
    private final String truncation;
    private final List<String> include;
    private final String serviceTier;
    private final String safetyIdentifier;
    private final String promptCacheKey;
    private final String promptCacheRetention;
    private final String reasoningEffort;
    private final String textVerbosity;
    private final Boolean streamIncludeObfuscation;
    private final Boolean store;
    private final Boolean strict;

    private OpenAiResponsesChatRequestParameters(Builder builder) {
        super(builder);
        this.previousResponseId = builder.previousResponseId;
        this.maxToolCalls = builder.maxToolCalls;
        this.parallelToolCalls = builder.parallelToolCalls;
        this.topLogprobs = builder.topLogprobs;
        this.truncation = builder.truncation;
        this.include = copyIfNotNull(builder.include);
        this.serviceTier = builder.serviceTier;
        this.safetyIdentifier = builder.safetyIdentifier;
        this.promptCacheKey = builder.promptCacheKey;
        this.promptCacheRetention = builder.promptCacheRetention;
        this.reasoningEffort = builder.reasoningEffort;
        this.textVerbosity = builder.textVerbosity;
        this.streamIncludeObfuscation = builder.streamIncludeObfuscation;
        this.store = builder.store;
        this.strict = builder.strict;
    }

    public String previousResponseId() {
        return previousResponseId;
    }

    public Integer maxToolCalls() {
        return maxToolCalls;
    }

    public Boolean parallelToolCalls() {
        return parallelToolCalls;
    }

    public Integer topLogprobs() {
        return topLogprobs;
    }

    public String truncation() {
        return truncation;
    }

    public List<String> include() {
        return include;
    }

    public String serviceTier() {
        return serviceTier;
    }

    public String safetyIdentifier() {
        return safetyIdentifier;
    }

    public String promptCacheKey() {
        return promptCacheKey;
    }

    public String promptCacheRetention() {
        return promptCacheRetention;
    }

    public String reasoningEffort() {
        return reasoningEffort;
    }

    public String textVerbosity() {
        return textVerbosity;
    }

    public Boolean streamIncludeObfuscation() {
        return streamIncludeObfuscation;
    }

    public Boolean store() {
        return store;
    }

    public Boolean strict() {
        return strict;
    }

    @Override
    public OpenAiResponsesChatRequestParameters overrideWith(ChatRequestParameters that) {
        return OpenAiResponsesChatRequestParameters.builder()
                .overrideWith(this)
                .overrideWith(that)
                .build();
    }

    @Override
    public OpenAiResponsesChatRequestParameters defaultedBy(ChatRequestParameters that) {
        return OpenAiResponsesChatRequestParameters.builder()
                .overrideWith(that)
                .overrideWith(this)
                .build();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        OpenAiResponsesChatRequestParameters that = (OpenAiResponsesChatRequestParameters) o;
        return Objects.equals(previousResponseId, that.previousResponseId)
                && Objects.equals(maxToolCalls, that.maxToolCalls)
                && Objects.equals(parallelToolCalls, that.parallelToolCalls)
                && Objects.equals(topLogprobs, that.topLogprobs)
                && Objects.equals(truncation, that.truncation)
                && Objects.equals(include, that.include)
                && Objects.equals(serviceTier, that.serviceTier)
                && Objects.equals(safetyIdentifier, that.safetyIdentifier)
                && Objects.equals(promptCacheKey, that.promptCacheKey)
                && Objects.equals(promptCacheRetention, that.promptCacheRetention)
                && Objects.equals(reasoningEffort, that.reasoningEffort)
                && Objects.equals(textVerbosity, that.textVerbosity)
                && Objects.equals(streamIncludeObfuscation, that.streamIncludeObfuscation)
                && Objects.equals(store, that.store)
                && Objects.equals(strict, that.strict);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                super.hashCode(),
                previousResponseId,
                maxToolCalls,
                parallelToolCalls,
                topLogprobs,
                truncation,
                include,
                serviceTier,
                safetyIdentifier,
                promptCacheKey,
                promptCacheRetention,
                reasoningEffort,
                textVerbosity,
                streamIncludeObfuscation,
                store,
                strict);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder extends DefaultChatRequestParameters.Builder<Builder> {

        private String previousResponseId;
        private Integer maxToolCalls;
        private Boolean parallelToolCalls;
        private Integer topLogprobs;
        private String truncation;
        private List<String> include;
        private String serviceTier;
        private String safetyIdentifier;
        private String promptCacheKey;
        private String promptCacheRetention;
        private String reasoningEffort;
        private String textVerbosity;
        private Boolean streamIncludeObfuscation;
        private Boolean store;
        private Boolean strict;

        @Override
        public Builder overrideWith(ChatRequestParameters parameters) {
            super.overrideWith(parameters);
            if (parameters instanceof OpenAiResponsesChatRequestParameters p) {
                previousResponseId(getOrDefault(p.previousResponseId(), previousResponseId));
                maxToolCalls(getOrDefault(p.maxToolCalls(), maxToolCalls));
                parallelToolCalls(getOrDefault(p.parallelToolCalls(), parallelToolCalls));
                topLogprobs(getOrDefault(p.topLogprobs(), topLogprobs));
                truncation(getOrDefault(p.truncation(), truncation));
                include(getOrDefault(p.include(), include));
                serviceTier(getOrDefault(p.serviceTier(), serviceTier));
                safetyIdentifier(getOrDefault(p.safetyIdentifier(), safetyIdentifier));
                promptCacheKey(getOrDefault(p.promptCacheKey(), promptCacheKey));
                promptCacheRetention(getOrDefault(p.promptCacheRetention(), promptCacheRetention));
                reasoningEffort(getOrDefault(p.reasoningEffort(), reasoningEffort));
                textVerbosity(getOrDefault(p.textVerbosity(), textVerbosity));
                streamIncludeObfuscation(getOrDefault(p.streamIncludeObfuscation(), streamIncludeObfuscation));
                store(getOrDefault(p.store(), store));
                strict(getOrDefault(p.strict(), strict));
            }
            return this;
        }

        public Builder previousResponseId(String previousResponseId) {
            this.previousResponseId = previousResponseId;
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

        public Builder topLogprobs(Integer topLogprobs) {
            this.topLogprobs = topLogprobs;
            return this;
        }

        public Builder truncation(String truncation) {
            this.truncation = truncation;
            return this;
        }

        public Builder include(List<String> include) {
            this.include = include;
            return this;
        }

        public Builder serviceTier(String serviceTier) {
            this.serviceTier = serviceTier;
            return this;
        }

        public Builder safetyIdentifier(String safetyIdentifier) {
            this.safetyIdentifier = safetyIdentifier;
            return this;
        }

        public Builder promptCacheKey(String promptCacheKey) {
            this.promptCacheKey = promptCacheKey;
            return this;
        }

        public Builder promptCacheRetention(String promptCacheRetention) {
            this.promptCacheRetention = promptCacheRetention;
            return this;
        }

        public Builder reasoningEffort(String reasoningEffort) {
            this.reasoningEffort = reasoningEffort;
            return this;
        }

        public Builder textVerbosity(String textVerbosity) {
            this.textVerbosity = textVerbosity;
            return this;
        }

        public Builder streamIncludeObfuscation(Boolean streamIncludeObfuscation) {
            this.streamIncludeObfuscation = streamIncludeObfuscation;
            return this;
        }

        public Builder store(Boolean store) {
            this.store = store;
            return this;
        }

        public Builder strict(Boolean strict) {
            this.strict = strict;
            return this;
        }

        @Override
        public OpenAiResponsesChatRequestParameters build() {
            return new OpenAiResponsesChatRequestParameters(this);
        }
    }
}
