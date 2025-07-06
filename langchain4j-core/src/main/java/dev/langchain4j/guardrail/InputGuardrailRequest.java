package dev.langchain4j.guardrail;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.data.message.ContentType;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import java.util.Objects;

/**
 * Represents the parameter passed to {@link InputGuardrail#validate(InputGuardrailRequest)}.
 */
public final class InputGuardrailRequest implements GuardrailRequest<InputGuardrailRequest> {
    private final UserMessage userMessage;
    private final GuardrailRequestParams commonParams;

    private InputGuardrailRequest(Builder builder) {
        this.userMessage = ensureNotNull(builder.userMessage, "userMessage");
        this.commonParams = ensureNotNull(builder.commonParams, "requestParams");
    }

    /**
     * Returns the user message.
     *
     * @return the user message
     */
    public UserMessage userMessage() {
        return userMessage;
    }

    /**
     * Returns the common parameters shared between types of guardrails.
     *
     * @return the common parameters
     */
    @Override
    public GuardrailRequestParams requestParams() {
        return commonParams;
    }

    @Override
    public InputGuardrailRequest withText(String text) {
        return new Builder()
                .userMessage(rewriteUserMessage(text))
                .commonParams(this.commonParams)
                .build();
    }

    public UserMessage rewriteUserMessage(String text) {
        if (Objects.isNull(this.userMessage) || Objects.isNull(text)) {
            return this.userMessage;
        }

        var rewrittenContent = this.userMessage.contents().stream()
                .map(c -> (c.type() == ContentType.TEXT) ? new TextContent(text) : c)
                .toList();

        return Objects.nonNull(this.userMessage.name())
                ? UserMessage.from(this.userMessage.name(), rewrittenContent)
                : UserMessage.from(rewrittenContent);
    }

    /**
     * Creates a new builder for {@link InputGuardrailRequest}.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link InputGuardrailRequest}.
     */
    public static class Builder {
        private UserMessage userMessage;
        private GuardrailRequestParams commonParams;

        /**
         * Sets the user message.
         *
         * @param userMessage the user message
         * @return this builder
         */
        public Builder userMessage(UserMessage userMessage) {
            this.userMessage = userMessage;
            return this;
        }

        /**
         * Sets the common parameters.
         *
         * @param commonParams the common parameters
         * @return this builder
         */
        public Builder commonParams(GuardrailRequestParams commonParams) {
            this.commonParams = commonParams;
            return this;
        }

        /**
         * Builds a new {@link InputGuardrailRequest}.
         *
         * @return a new {@link InputGuardrailRequest}
         */
        public InputGuardrailRequest build() {
            return new InputGuardrailRequest(this);
        }
    }
}
