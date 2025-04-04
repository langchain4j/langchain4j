package dev.langchain4j.guardrail;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.data.message.ContentType;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Represents the parameter passed to {@link InputGuardrail#validate(InputGuardrailParams)}.
 *
 * @param userMessage
 *            The user message
 * @param commonParams
 *            The common params shared between types of guardrails
 */
public record InputGuardrailParams(UserMessage userMessage, CommonGuardrailParams commonParams)
        implements GuardrailParams<InputGuardrailParams> {

    public InputGuardrailParams {
        ensureNotNull(userMessage, "userMessage");
        ensureNotNull(commonParams, "commonParams");
    }

    @Override
    public InputGuardrailParams withText(@Nullable String text) {
        return new InputGuardrailParams(rewriteUserMessage(text), this.commonParams);
    }

    public UserMessage rewriteUserMessage(@Nullable String text) {
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
}
