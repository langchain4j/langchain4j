package dev.langchain4j.service;

import static dev.langchain4j.model.openai.internal.OpenAiUtils.aiMessageFrom;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.moderation.Moderation;
import dev.langchain4j.model.openai.internal.chat.AssistantMessage;
import dev.langchain4j.model.openai.internal.chat.ChatCompletionChoice;
import dev.langchain4j.model.openai.internal.chat.ChatCompletionResponse;
import dev.langchain4j.model.openai.internal.chat.FunctionCall;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

class AiServicesModerationTest {

    @Test
    void should_throw_ModerationException_when_content_is_flagged() {
        // Given
        String flaggedText = "inappropriate content";
        final var flaggedModeration = Moderation.flagged(flaggedText);

        // Create a Future that will return the flagged moderation
        final var moderationFuture = CompletableFuture.completedFuture(flaggedModeration);

        // When/Then
        assertThatThrownBy(() -> AiServices.verifyModerationIfNeeded(moderationFuture))
                .isInstanceOf(ModerationException.class)
                .hasMessageContaining("violates content policy")
                .hasMessageContaining(flaggedText)
                .extracting(ex -> ((ModerationException) ex).moderation())
                .isSameAs(flaggedModeration);
    }

    @Test
    void should_NOT_throw_ModerationException_when_content_is_NOT_flagged() {
        // Given
        final var safeModeration = Moderation.notFlagged();
        final var moderationFuture = CompletableFuture.completedFuture(safeModeration);

        // When/Then - should not throw an exception
        assertDoesNotThrow(() -> AiServices.verifyModerationIfNeeded(moderationFuture));
    }

    @Test
    void should_do_nothing_when_no_moderation_is_provided() {
        assertDoesNotThrow(() -> AiServices.verifyModerationIfNeeded(null));
    }

    @Test
    void should_preserve_moderation_object_in_exception() {
        // Given
        String flaggedText = "test content";
        final var originalModeration = Moderation.flagged(flaggedText);
        final var moderationFuture = CompletableFuture.completedFuture(originalModeration);

        // When/Then
        assertThatThrownBy(() -> AiServices.verifyModerationIfNeeded(moderationFuture))
                .isInstanceOf(ModerationException.class)
                .satisfies(exception -> {
                    ModerationException moderationException = (ModerationException) exception;
                    assert moderationException.moderation() == originalModeration;
                    assert moderationException.moderation().flaggedText().equals(flaggedText);
                });
    }

    @Test
    void should_handle_whitespace_only_flagged_text() {
        // Given
        final var flaggedModeration = Moderation.flagged("   ");
        final var moderationFuture = CompletableFuture.completedFuture(flaggedModeration);

        // When/Then
        assertThatThrownBy(() -> AiServices.verifyModerationIfNeeded(moderationFuture))
                .isInstanceOf(ModerationException.class);
    }

    @Test
    void should_handle_completed_future_multiple_times() {
        // Given
        final var safeModeration = Moderation.notFlagged();
        final var moderationFuture = CompletableFuture.completedFuture(safeModeration);

        // When/Then - same future can be checked multiple times
        assertDoesNotThrow(() -> AiServices.verifyModerationIfNeeded(moderationFuture));
        assertDoesNotThrow(() -> AiServices.verifyModerationIfNeeded(moderationFuture));
    }

    @Test
    void should_handle_empty_response_choices() {
        // given
        ChatCompletionResponse response = ChatCompletionResponse.builder()
                .choices(Collections.emptyList())
                .build();

        // when/then
        assertThatThrownBy(() -> aiMessageFrom(response)).isInstanceOf(IndexOutOfBoundsException.class);
    }

    @Test
    void should_handle_null_message_content() {
        // given
        ChatCompletionResponse response = ChatCompletionResponse.builder()
                .choices(singletonList(ChatCompletionChoice.builder()
                        .message(AssistantMessage.builder().content(null).build())
                        .build()))
                .build();

        // when
        AiMessage aiMessage = aiMessageFrom(response);

        // then
        assertThat(aiMessage.text()).isNull();
        assertThat(aiMessage.toolExecutionRequests()).isEmpty();
    }

    @Test
    void should_handle_function_call_with_null_arguments() {
        // given
        String functionName = "simple_function";

        ChatCompletionResponse response = ChatCompletionResponse.builder()
                .choices(singletonList(ChatCompletionChoice.builder()
                        .message(AssistantMessage.builder()
                                .functionCall(FunctionCall.builder()
                                        .name(functionName)
                                        .arguments(null)
                                        .build())
                                .build())
                        .build()))
                .build();

        // when
        AiMessage aiMessage = aiMessageFrom(response);

        // then
        assertThat(aiMessage.toolExecutionRequests())
                .containsExactly(ToolExecutionRequest.builder()
                        .name(functionName)
                        .arguments(null)
                        .build());
    }
}
