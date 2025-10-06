package dev.langchain4j.model.openai.responses;

import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.Utils.getOrDefault;

import com.openai.models.responses.ResponseOutputItem;
import dev.langchain4j.Experimental;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.DefaultChatRequestParameters;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Experimental
public class OpenAiResponsesChatRequestParameters extends DefaultChatRequestParameters {

    public static final OpenAiResponsesChatRequestParameters EMPTY =
            OpenAiResponsesChatRequestParameters.builder().build();

    private final Integer maxCompletionTokens;
    private final Map<String, Integer> logitBias;
    private final Boolean parallelToolCalls;
    private final Integer seed;
    private final String user;
    private final Boolean store;
    private final Map<String, String> metadata;
    private final String serviceTier;
    private final String reasoningEffort;

    // Responses API specific fields
    private final String instructions;
    private final List<String> include;
    private final List<ResponseOutputItem> previousOutputItems;

    private OpenAiResponsesChatRequestParameters(Builder builder) {
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
        this.instructions = builder.instructions;
        this.include = copy(builder.include);
        this.previousOutputItems = copy(builder.previousOutputItems);
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

    public String instructions() {
        return instructions;
    }

    public List<String> include() {
        return include;
    }

    public List<ResponseOutputItem> previousOutputItems() {
        return previousOutputItems;
    }

    public boolean isStatelessMode() {
        return Boolean.FALSE.equals(store) && include != null && include.contains("reasoning.encrypted_content");
    }

    @Override
    public OpenAiResponsesChatRequestParameters overrideWith(ChatRequestParameters that) {
        return OpenAiResponsesChatRequestParameters.builder()
                .overrideWith(this)
                .overrideWith(that)
                .build();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        OpenAiResponsesChatRequestParameters that = (OpenAiResponsesChatRequestParameters) o;
        return Objects.equals(maxCompletionTokens, that.maxCompletionTokens)
                && Objects.equals(logitBias, that.logitBias)
                && Objects.equals(parallelToolCalls, that.parallelToolCalls)
                && Objects.equals(seed, that.seed)
                && Objects.equals(user, that.user)
                && Objects.equals(store, that.store)
                && Objects.equals(metadata, that.metadata)
                && Objects.equals(serviceTier, that.serviceTier)
                && Objects.equals(reasoningEffort, that.reasoningEffort)
                && Objects.equals(instructions, that.instructions)
                && Objects.equals(include, that.include)
                && Objects.equals(previousOutputItems, that.previousOutputItems);
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
                instructions,
                include,
                previousOutputItems);
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
        private String instructions;
        private List<String> include;
        private List<ResponseOutputItem> previousOutputItems;

        @Override
        public Builder overrideWith(ChatRequestParameters parameters) {
            super.overrideWith(parameters);
            if (parameters instanceof OpenAiResponsesChatRequestParameters responsesParams) {
                maxCompletionTokens(getOrDefault(responsesParams.maxCompletionTokens(), maxCompletionTokens));
                logitBias(getOrDefault(responsesParams.logitBias(), logitBias));
                parallelToolCalls(getOrDefault(responsesParams.parallelToolCalls(), parallelToolCalls));
                seed(getOrDefault(responsesParams.seed(), seed));
                user(getOrDefault(responsesParams.user(), user));
                store(getOrDefault(responsesParams.store(), store));
                metadata(getOrDefault(responsesParams.metadata(), metadata));
                serviceTier(getOrDefault(responsesParams.serviceTier(), serviceTier));
                reasoningEffort(getOrDefault(responsesParams.reasoningEffort(), reasoningEffort));
                instructions(getOrDefault(responsesParams.instructions(), instructions));
                include(getOrDefault(responsesParams.include(), include));
                previousOutputItems(getOrDefault(responsesParams.previousOutputItems(), previousOutputItems));
            }
            return this;
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

        public Builder instructions(String instructions) {
            this.instructions = instructions;
            return this;
        }

        public Builder include(List<String> include) {
            this.include = include;
            return this;
        }

        public Builder previousOutputItems(List<ResponseOutputItem> previousOutputItems) {
            this.previousOutputItems = previousOutputItems;
            return this;
        }

        @Override
        public OpenAiResponsesChatRequestParameters build() {
            return new OpenAiResponsesChatRequestParameters(this);
        }
    }
}
