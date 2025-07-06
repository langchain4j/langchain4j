package dev.langchain4j.model.bedrock;

import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.DefaultChatRequestParameters;

import java.util.HashMap;
import java.util.Map;

import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.Utils.getOrDefault;

public class BedrockChatRequestParameters extends DefaultChatRequestParameters {

    public static final BedrockChatRequestParameters EMPTY = BedrockChatRequestParameters.builder().build();

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
                additionalModelRequestFields(getOrDefault(bedrockRequestParameters.additionalModelRequestFields, additionalModelRequestFields));
            }
            return this;
        }

        public Builder additionalModelRequestFields(Map<String, Object> additionalModelRequestFields) {
            this.additionalModelRequestFields = additionalModelRequestFields;
            return this;
        }

        public Builder enableReasoning(Integer tokenBudget) {
            if (tokenBudget != null) {
                if (additionalModelRequestFields == null) {
                    additionalModelRequestFields = new HashMap<>();
                }
                Map<?, ?> reasoningConfig = Map.ofEntries(
                        Map.entry("type", "enabled"),
                        Map.entry("budget_tokens", tokenBudget)
                );
                additionalModelRequestFields.put("reasoning_config", reasoningConfig);
            }
            return this;
        }

        @Override
        public BedrockChatRequestParameters build() {
            return new BedrockChatRequestParameters(this);
        }
    }
}
