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

    /**
     * Enum representing where to place cache points in the conversation
     */
    public enum CachePointPlacement {
        AFTER_SYSTEM,
        AFTER_USER_MESSAGE,
        AFTER_TOOLS
    }

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
         * Enables prompt caching for supported models (Claude 3.5 Sonnet, Claude 3.5 Haiku, etc.).
         * This allows caching of frequently used prompts to reduce latency and costs.
         *
         * @param enabled true to enable prompt caching
         * @return this builder
         * @see <a href="https://docs.aws.amazon.com/bedrock/latest/userguide/prompt-caching.html">AWS Bedrock Prompt Caching</a>
         */
        public Builder enablePromptCaching(boolean enabled) {
            if (enabled) {
                if (additionalModelRequestFields == null) {
                    additionalModelRequestFields = new HashMap<>();
                }
                additionalModelRequestFields.put("promptCaching", Map.of("enabled", true));
            }
            return this;
        }

        /**
         * Sets where to place the cache point in the conversation.
         * Cache points mark where to cache content for reuse across API calls.
         * The cache has a 5-minute TTL which resets on each cache hit.
         *
         * @param placement where to place the cache point
         * @return this builder
         * @see <a href="https://docs.aws.amazon.com/bedrock/latest/userguide/prompt-caching.html">AWS Bedrock Prompt Caching</a>
         */
        public Builder cachePoint(CachePointPlacement placement) {
            if (placement != null) {
                if (additionalModelRequestFields == null) {
                    additionalModelRequestFields = new HashMap<>();
                }

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
