package dev.langchain4j.guardrails;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.guardrail.InputGuardrail;
import dev.langchain4j.guardrail.InputGuardrailResult;
import dev.langchain4j.model.moderation.Moderation;
import dev.langchain4j.model.moderation.ModerationModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.service.ModerationException;
import java.util.stream.Collectors;

/**
 * An {@link InputGuardrail} that validates user messages using a {@link ModerationModel} to detect
 * potentially harmful, inappropriate, or policy-violating content.
 * <p>
 * This guardrail checks incoming user messages for content that should be moderated, such as
 * hate speech, violence, self-harm, sexual content, or other categories defined by the moderation model.
 * If the message is flagged by the moderation model, the validation fails with a fatal result,
 * preventing the message from being processed further.
 * </p>
 * <p>
 * This is useful for ensuring that user inputs comply with content policies before being sent
 * to an LLM or processed by the application.
 * </p>
 */
public class MessageModeratorInputGuardrail implements InputGuardrail {

    private final ModerationModel moderationModel;

    /**
     * Constructs a new {@link MessageModeratorInputGuardrail} with the specified moderation model.
     *
     * @param moderationModel the {@link ModerationModel} to use for validating user messages.
     *                        Must not be null.
     */
    public MessageModeratorInputGuardrail(ModerationModel moderationModel) {
        this.moderationModel = ensureNotNull(moderationModel, "moderationModel");
    }

    /**
     * Validates the provided user message using the configured {@link ModerationModel}.
     * <p>
     * If the moderation model flags the message as inappropriate or policy-violating,
     * this method returns a fatal result with a {@link ModerationException}.
     * Otherwise, it returns a successful validation result.
     * </p>
     *
     * @param userMessage the {@link UserMessage} to validate. Must not be null.
     * @return an {@link InputGuardrailResult} indicating success if the message passes moderation,
     * or a fatal result with a {@link ModerationException} if the message is flagged
     */
    @Override
    public InputGuardrailResult validate(UserMessage userMessage) {
        // The user message can be multimodal (text plus an image) or contain several text parts.
        // For such messages UserMessage.singleText() throws, so extract all text content and
        // moderate it as a plain text message. Single-text messages are moderated as-is to keep
        // the existing behaviour (ModerationModel moderates plain text anyway).
        UserMessage messageToModerate =
                userMessage.hasSingleText() ? userMessage : UserMessage.from(extractText(userMessage));

        Response<Moderation> response = moderationModel.moderate(messageToModerate);

        if (response.content().flagged()) {
            return fatal(
                    "User message has been flagged",
                    new ModerationException("User message has been flagged", response.content()));
        } else {
            return success();
        }
    }

    private static String extractText(UserMessage userMessage) {
        return userMessage.contents().stream()
                .filter(TextContent.class::isInstance)
                .map(TextContent.class::cast)
                .map(TextContent::text)
                .collect(Collectors.joining());
    }
}
