package dev.langchain4j.guardrails;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.guardrail.GuardrailResult;
import dev.langchain4j.guardrail.InputGuardrailResult;
import dev.langchain4j.model.moderation.Moderation;
import dev.langchain4j.model.moderation.ModerationModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.service.ModerationException;
import org.junit.jupiter.api.Test;

import static dev.langchain4j.test.guardrail.GuardrailAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MessageModeratorInputGuardrailTest {

    @Test
    void should_return_success_when_content_is_safe() {
        // Given
        ModerationModel moderationModel = mock(ModerationModel.class);
        Moderation moderation = Moderation.notFlagged();
        Response<Moderation> response = Response.from(moderation);
        when(moderationModel.moderate(any(UserMessage.class))).thenReturn(response);

        MessageModeratorInputGuardrail moderatorInputGuardrail = new MessageModeratorInputGuardrail(moderationModel);
        UserMessage userMessage = UserMessage.from("This is a safe message");

        // When
        InputGuardrailResult result = moderatorInputGuardrail.validate(userMessage);

        // Then
        assertThat(result)
                .isNotNull()
                .extracting(InputGuardrailResult::result)
                .isEqualTo(GuardrailResult.Result.SUCCESS);

        verify(moderationModel).moderate(userMessage);
    }

    @Test
    void should_return_fatal_when_content_is_not_safe() {
        // Given
        ModerationModel moderationModel = mock(ModerationModel.class);
        Moderation moderation = Moderation.flagged("I kill you!");
        Response<Moderation> response = Response.from(moderation);
        when(moderationModel.moderate(any(UserMessage.class))).thenReturn(response);

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

        verify(moderationModel).moderate(userMessage);
    }

    @Test
    void should_process_several_safe_messages() {
        // Given
        ModerationModel moderationModel = mock(ModerationModel.class);
        Moderation moderation = Moderation.notFlagged();
        Response<Moderation> response = Response.from(moderation);
        when(moderationModel.moderate(any(UserMessage.class))).thenReturn(response);

        MessageModeratorInputGuardrail moderatorInputGuardrail = new MessageModeratorInputGuardrail(moderationModel);
        UserMessage textMessage = UserMessage.from("Hello, how are you?");
        UserMessage questionMessage = UserMessage.from("What is the weather today?");

        // When
        InputGuardrailResult result1 = moderatorInputGuardrail.validate(textMessage);
        InputGuardrailResult result2 = moderatorInputGuardrail.validate(questionMessage);

        // Then
        assertThat(result1.result()).isEqualTo(GuardrailResult.Result.SUCCESS);
        assertThat(result2.result()).isEqualTo(GuardrailResult.Result.SUCCESS);

        verify(moderationModel).moderate(textMessage);
        verify(moderationModel).moderate(questionMessage);
    }

    @Test
    void should_throw_exception_when_moderation_model_is_null() {
        assertThatThrownBy(() -> new MessageModeratorInputGuardrail(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("moderationModel cannot be null");
    }
}

