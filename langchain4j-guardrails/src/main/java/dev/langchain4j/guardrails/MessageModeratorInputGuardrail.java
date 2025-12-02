package dev.langchain4j.guardrails;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.guardrail.InputGuardrail;
import dev.langchain4j.guardrail.InputGuardrailResult;
import dev.langchain4j.model.moderation.Moderation;
import dev.langchain4j.model.moderation.ModerationModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.service.ModerationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private static final Logger LOG = LoggerFactory.getLogger(MessageModeratorInputGuardrail.class);

    private final ModerationModel moderationModel;

    /**
     * Constructs a new {@link MessageModeratorInputGuardrail} with the specified moderation model.
     *
     * @param moderationModel the {@link ModerationModel} to use for validating user messages.
     *                        Must not be null.
     */
    public MessageModeratorInputGuardrail(ModerationModel moderationModel) {
        this.moderationModel = moderationModel;
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
        Response<Moderation> response = moderationModel.moderate(userMessage);

        if (response.content().flagged()) {
            return fatal("User message has been flagged", new ModerationException("User message has been flagged", response.content()));
        } else {
            return success();
        }
    }
}
