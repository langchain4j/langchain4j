package dev.langchain4j.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import dev.langchain4j.model.moderation.Moderation;
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
}
