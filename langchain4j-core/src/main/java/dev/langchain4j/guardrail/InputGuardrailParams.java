package dev.langchain4j.guardrail;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.data.message.ContentType;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.rag.AugmentationResult;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * Represents the parameter passed to {@link InputGuardrail#validate(InputGuardrailParams)}.
 *
 * @param userMessage
 *            The user message
 * @param chatMemory
 *            The memory
 * @param augmentationResult
 *            The augmentation result
 * @param userMessageTemplate
 *            The user message template
 * @param variables
 *            The variables to be used in the {@link #userMessageTemplate()}
 */
public record InputGuardrailParams(
        UserMessage userMessage,
        @Nullable ChatMemory chatMemory,
        @Nullable AugmentationResult augmentationResult,
        String userMessageTemplate,
        Map<String, Object> variables)
        implements GuardrailParams<InputGuardrailParams> {

    public InputGuardrailParams {
        ensureNotNull(userMessage, "userMessage");
        ensureNotNull(userMessageTemplate, "userMessageTemplate");
        ensureNotNull(variables, "variables cannot be null");
    }

    /**
     * Create an {@link InputGuardrailParams} from a {@link UserMessage}
     */
    public static InputGuardrailParams from(UserMessage userMessage) {
        return from(userMessage, null);
    }

    /**
     * Create an {@link InputGuardrailParams}
     */
    public static InputGuardrailParams from(UserMessage userMessage, @Nullable Map<String, Object> variables) {
        return new InputGuardrailParams(
                userMessage, null, null, "", Optional.ofNullable(variables).orElseGet(Map::of));
    }

    @Override
    public InputGuardrailParams withText(@Nullable String text) {
        return new InputGuardrailParams(
                rewriteUserMessage(text),
                this.chatMemory,
                this.augmentationResult,
                this.userMessageTemplate,
                this.variables);
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
