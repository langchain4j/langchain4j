package dev.langchain4j.guardrails;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.guardrail.GuardrailResult;
import dev.langchain4j.guardrail.InputGuardrailResult;
import dev.langchain4j.model.moderation.ModerationModel;
import dev.langchain4j.service.ModerationException;
import dev.langchain4j.model.mistralai.MistralAiModerationModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static dev.langchain4j.test.guardrail.GuardrailAssertions.assertThat;

@EnabledIfEnvironmentVariable(named = "MISTRAL_AI_API_KEY", matches = ".+")
class MessageModeratorInputGuardrailIT {

    ModerationModel moderationModel = new MistralAiModerationModel.Builder()
            .apiKey(System.getenv("MISTRAL_AI_API_KEY"))
            .modelName("mistral-moderation-latest")
            .logRequests(true)
            .logResponses(true)
            .build();

    @Test
    void should_return_success_when_content_is_safe() {
        // Given
        MessageModeratorInputGuardrail moderatorInputGuardrail = new MessageModeratorInputGuardrail(moderationModel);
        UserMessage userMessage = UserMessage.from("This is a safe message");

        // When
        InputGuardrailResult result = moderatorInputGuardrail.validate(userMessage);

        // Then
        assertThat(result)
                .isNotNull()
                .extracting(InputGuardrailResult::result)
                .isEqualTo(GuardrailResult.Result.SUCCESS);
    }

    @Test
    void should_return_fatal_when_content_is_not_safe() {
        // Given
        MessageModeratorInputGuardrail moderatorInputGuardrail = new MessageModeratorInputGuardrail(moderationModel);
        UserMessage userMessage = UserMessage.from("I kill you!");

        // When
        InputGuardrailResult result = moderatorInputGuardrail.validate(userMessage);

        // Then
        assertThat(result)
                .isNotNull()
                .extracting(InputGuardrailResult::result)
                .isEqualTo(GuardrailResult.Result.FATAL);

        assertThat(result.getFirstFailureException())
                .isNotNull()
                .isInstanceOf(ModerationException.class)
                .hasMessage("User message has been flagged");
    }

    @Test
    void should_process_several_safe_messages() {
        // Given
        MessageModeratorInputGuardrail moderatorInputGuardrail = new MessageModeratorInputGuardrail(moderationModel);
        UserMessage textMessage = UserMessage.from("Hello, how are you?");
        UserMessage questionMessage = UserMessage.from("What is the weather today?");

        // When
        InputGuardrailResult result1 = moderatorInputGuardrail.validate(textMessage);
        InputGuardrailResult result2 = moderatorInputGuardrail.validate(questionMessage);

        // Then
        assertThat(result1.result()).isEqualTo(GuardrailResult.Result.SUCCESS);
        assertThat(result2.result()).isEqualTo(GuardrailResult.Result.SUCCESS);
    }
}

