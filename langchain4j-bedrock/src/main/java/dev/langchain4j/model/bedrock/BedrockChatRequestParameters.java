package dev.langchain4j.model.bedrock;

import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.Utils.getOrDefault;

import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.DefaultChatRequestParameters;
import java.util.HashMap;
import java.util.Map;

public class BedrockChatRequestParameters extends DefaultChatRequestParameters {

    public static final BedrockChatRequestParameters EMPTY =
            BedrockChatRequestParameters.builder().build();

    private final Map<String, Object> additionalModelRequestFields;

    private BedrockChatRequestParameters(Builder builder) {
        super(builder);
        this.additionalModelRequestFields = copy(builder.additionalModelRequestFields);
    }

    @Override
    public BedrockChatRequestParameters overrideWith(ChatRequestParameters that) {
        return BedrockChatRequestParameters.builder()
                .overrideWith(this)
                .overrideWith(that)
                .build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public Map<String, Object> additionalModelRequestFields() {
        return additionalModelRequestFields;
    }

    public static class Builder extends DefaultChatRequestParameters.Builder<Builder> {

        private Map<String, Object> additionalModelRequestFields;

        @Override
        public Builder overrideWith(ChatRequestParameters parameters) {
            super.overrideWith(parameters);
            if (parameters instanceof BedrockChatRequestParameters bedrockRequestParameters) {
                additionalModelRequestFields(getOrDefault(
                        bedrockRequestParameters.additionalModelRequestFields, additionalModelRequestFields));
            }
            return this;
        }

        public Builder additionalModelRequestFields(Map<String, Object> additionalModelRequestFields) {
            this.additionalModelRequestFields = additionalModelRequestFields;
            return this;
        }

        public Builder additionalModelRequestField(String key, Object value) {
            if (additionalModelRequestFields == null) {
                additionalModelRequestFields = new HashMap<>();
            }
            additionalModelRequestFields.put(key, value);
            return this;
        }

        /**
         * Enables <a href="https://docs.aws.amazon.com/bedrock/latest/userguide/inference-reasoning.html">reasoning</a>.
         *
         * @see BedrockChatModel.Builder#returnThinking(Boolean)
         * @see BedrockChatModel.Builder#sendThinking(Boolean)
         */
        public Builder enableReasoning(Integer tokenBudget) {
            if (tokenBudget != null) {
                if (additionalModelRequestFields == null) {
                    additionalModelRequestFields = new HashMap<>();
                }
                Map<?, ?> reasoningConfig =
                        Map.ofEntries(Map.entry("type", "enabled"), Map.entry("budget_tokens", tokenBudget));
                additionalModelRequestFields.put("reasoning_config", reasoningConfig);
            }
            return this;
        }

        /**
         * Enables prompt caching and sets where to place the cache point in the conversation.
         * Cache points mark where to cache content for reuse across API calls.
         * The cache has a 5-minute TTL which resets on each cache hit.
         * Supported models include Claude 3.5 Sonnet, Claude 3.5 Haiku, Amazon Nova, etc.
         * This can reduce latency by up to 85% and costs by up to 90% for cached prompts.
         *
         * @param placement where to place the cache point (null disables caching)
         * @return this builder
         * @see <a href="https://docs.aws.amazon.com/bedrock/latest/userguide/prompt-caching.html">AWS Bedrock Prompt Caching</a>
         */
        public Builder promptCaching(BedrockCachePointPlacement placement) {
            if (placement != null) {
                if (additionalModelRequestFields == null) {
                    additionalModelRequestFields = new HashMap<>();
                }

                // Enable caching
                additionalModelRequestFields.put("promptCaching", Map.of("enabled", true));

                // Set cache point placement
                Map<String, Object> cachePoint = Map.of("cachePoint", Map.of("type", "default"));
                additionalModelRequestFields.put("cachePointPlacement", placement.name());
                additionalModelRequestFields.put("cachePointData", cachePoint);
            }
            return this;
        }

        @Override
        public BedrockChatRequestParameters build() {
            return new BedrockChatRequestParameters(this);
        }
    }
}
