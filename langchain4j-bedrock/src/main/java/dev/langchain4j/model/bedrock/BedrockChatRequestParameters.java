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
    private final BedrockCachePointPlacement cachePointPlacement;
    private final BedrockGuardrailConfiguration bedrockGuardrailConfiguration;

    private BedrockChatRequestParameters(Builder builder) {
        super(builder);
        this.additionalModelRequestFields = copy(builder.additionalModelRequestFields);
        this.cachePointPlacement = builder.cachePointPlacement;
        this.bedrockGuardrailConfiguration = builder.bedrockGuardrailConfiguration;
    }

    @Override
    public BedrockChatRequestParameters overrideWith(ChatRequestParameters that) {
        return BedrockChatRequestParameters.builder()
                .overrideWith(this)
                .overrideWith(that)
                .build();
    }

    @Override
    public BedrockChatRequestParameters defaultedBy(ChatRequestParameters that) {
        return BedrockChatRequestParameters.builder()
                .overrideWith(that)
                .overrideWith(this)
                .build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public Map<String, Object> additionalModelRequestFields() {
        return additionalModelRequestFields;
    }

    public BedrockCachePointPlacement cachePointPlacement() {
        return cachePointPlacement;
    }

    public BedrockGuardrailConfiguration bedrockGuardrailConfiguration() {
        return bedrockGuardrailConfiguration;
    }

    public static class Builder extends DefaultChatRequestParameters.Builder<Builder> {

        private Map<String, Object> additionalModelRequestFields;
        private BedrockCachePointPlacement cachePointPlacement;
        private BedrockGuardrailConfiguration bedrockGuardrailConfiguration;

        @Override
        public Builder overrideWith(ChatRequestParameters parameters) {
            super.overrideWith(parameters);
            if (parameters instanceof BedrockChatRequestParameters bedrockRequestParameters) {
                // Merge additional model request fields instead of replacing
                if (bedrockRequestParameters.additionalModelRequestFields != null
                        && !bedrockRequestParameters.additionalModelRequestFields.isEmpty()) {
                    if (additionalModelRequestFields == null) {
                        additionalModelRequestFields = new HashMap<>();
                    }
                    additionalModelRequestFields.putAll(bedrockRequestParameters.additionalModelRequestFields);
                }
                this.cachePointPlacement =
                        getOrDefault(bedrockRequestParameters.cachePointPlacement, cachePointPlacement);
                this.bedrockGuardrailConfiguration = getOrDefault(
                        bedrockRequestParameters.bedrockGuardrailConfiguration, bedrockGuardrailConfiguration);
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
            this.cachePointPlacement = placement;
            return this;
        }

        /**
         * Configuration information for a <a href="https://docs.aws.amazon.com/bedrock/latest/userguide/guardrails.html">guardrail</a>
         * that you want to use in the request.
         *
         * @param bedrockGuardrailConfiguration the configuration details for the guardrail
         * @return this builder
         */
        public Builder guardrailConfiguration(BedrockGuardrailConfiguration bedrockGuardrailConfiguration) {
            this.bedrockGuardrailConfiguration = bedrockGuardrailConfiguration;
            return this;
        }

        @Override
        public BedrockChatRequestParameters build() {
            return new BedrockChatRequestParameters(this);
        }
    }
}
