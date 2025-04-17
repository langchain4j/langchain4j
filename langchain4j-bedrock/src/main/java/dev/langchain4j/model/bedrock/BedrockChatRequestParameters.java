package dev.langchain4j.model.bedrock;

import dev.langchain4j.Experimental;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.DefaultChatRequestParameters;

import java.util.HashMap;
import java.util.Map;

import static java.util.Objects.nonNull;

@Experimental
public class BedrockChatRequestParameters extends DefaultChatRequestParameters {

    private final Map<String, Object> additionalModelRequestFields;

    private BedrockChatRequestParameters(Builder builder) {
        super(builder);
        this.additionalModelRequestFields = builder.additionalModelRequestFields;
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

        private Map<String, Object> additionalModelRequestFields = new HashMap<>();

        @Override
        public Builder overrideWith(ChatRequestParameters parameters) {
            super.overrideWith(parameters);
            if (parameters instanceof BedrockChatRequestParameters bedrockRequestParameters) {
                if (nonNull(bedrockRequestParameters.additionalModelRequestFields)) {
                    additionalModelRequestFields.putAll(bedrockRequestParameters.additionalModelRequestFields);
                }
            }
            return this;
        }

        public Builder additionalModelRequestFields(Map<String, Object> additionalModelRequestFields) {
            if (nonNull(additionalModelRequestFields)) this.additionalModelRequestFields = additionalModelRequestFields;
            else this.additionalModelRequestFields = new HashMap<>();
            return this;
        }

        /**
         * @deprecated please use {@link #enableReasoning(Integer)} instead
         */
        @Deprecated(forRemoval = true)
        public Builder enableReasoning(Long tokenBudget) {
            this.additionalModelRequestFields.put(
                    "reasoning_config",
                    Map.ofEntries(Map.entry("type", "enabled"), Map.entry("budget_tokens", tokenBudget)));
            return this;
        }

        public Builder enableReasoning(Integer tokenBudget) {
            if (tokenBudget != null) {
                this.additionalModelRequestFields.put(
                        "reasoning_config",
                        Map.ofEntries(Map.entry("type", "enabled"), Map.entry("budget_tokens", tokenBudget)));
            }
            return this;
        }

        @Override
        public BedrockChatRequestParameters build() {
            return new BedrockChatRequestParameters(this);
        }
    }
}
