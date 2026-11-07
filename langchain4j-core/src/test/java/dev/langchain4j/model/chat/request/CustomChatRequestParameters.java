package dev.langchain4j.model.chat.request;

import static dev.langchain4j.internal.Utils.getOrDefault;

/**
 * Emulates provider-specific {@link ChatRequestParameters}, such as {@code OpenAiChatRequestParameters}.
 */
public class CustomChatRequestParameters extends DefaultChatRequestParameters {

    private final String customParameter;

    protected CustomChatRequestParameters(Builder builder) {
        super(builder);
        this.customParameter = builder.customParameter;
    }

    public String customParameter() {
        return customParameter;
    }

    @Override
    public CustomChatRequestParameters overrideWith(ChatRequestParameters that) {
        return CustomChatRequestParameters.builder()
                .overrideWith(this)
                .overrideWith(that)
                .build();
    }

    @Override
    public CustomChatRequestParameters defaultedBy(ChatRequestParameters that) {
        return CustomChatRequestParameters.builder()
                .overrideWith(that)
                .overrideWith(this)
                .build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder extends DefaultChatRequestParameters.Builder<Builder> {

        private String customParameter;

        @Override
        public Builder overrideWith(ChatRequestParameters parameters) {
            super.overrideWith(parameters);
            if (parameters instanceof CustomChatRequestParameters that) {
                customParameter(getOrDefault(that.customParameter(), customParameter));
            }
            return this;
        }

        public Builder customParameter(String customParameter) {
            this.customParameter = customParameter;
            return this;
        }

        @Override
        public CustomChatRequestParameters build() {
            return new CustomChatRequestParameters(this);
        }
    }
}
