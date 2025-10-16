package dev.langchain4j.model.anthropic;

import static dev.langchain4j.model.anthropic.InternalAnthropicHelper.validate;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.ResponseFormat;
import org.junit.jupiter.api.Test;

class InternalAnthropicHelperTest {

    @Test
    void validate_WithNoUnsupportedFeatures_ShouldNotThrowException() {
        // Given
        ChatRequestParameters parameters = ChatRequestParameters.builder()
                .responseFormat(ResponseFormat.TEXT)
                .build();

        // When-Then
        assertDoesNotThrow(() -> validate(parameters));
    }

    @Test
    void validate_WithJsonResponseFormat_ShouldThrowException() {
        // Given
        ChatRequestParameters parameters = ChatRequestParameters.builder()
                .responseFormat(ResponseFormat.JSON)
                .build();

        // When
        UnsupportedFeatureException exception =
                assertThrows(UnsupportedFeatureException.class, () -> validate(parameters));

        // Then
        assertEquals("JSON response format is not supported by Anthropic", exception.getMessage());
    }

    @Test
    void validate_WithFrequencyPenalty_ShouldThrowException() {
        // Given
        ChatRequestParameters parameters = ChatRequestParameters.builder()
                .responseFormat(ResponseFormat.TEXT)
                .frequencyPenalty(0.5)
                .build();

        // When
        UnsupportedFeatureException exception =
                assertThrows(UnsupportedFeatureException.class, () -> validate(parameters));

        // Then
        assertEquals("Frequency Penalty is not supported by Anthropic", exception.getMessage());
    }

    @Test
    void validate_WithPresencePenalty_ShouldThrowException() {
        // Given
        ChatRequestParameters parameters = ChatRequestParameters.builder()
                .responseFormat(ResponseFormat.TEXT)
                .presencePenalty(0.5)
                .build();

        // When
        UnsupportedFeatureException exception =
                assertThrows(UnsupportedFeatureException.class, () -> validate(parameters));

        // Then
        assertEquals("Presence Penalty is not supported by Anthropic", exception.getMessage());
    }

    @Test
    void validate_WithTwoUnsupportedFeatures_ShouldThrowExceptionWithCombinedMessage() {
        // Given
        ChatRequestParameters parameters = ChatRequestParameters.builder()
                .responseFormat(ResponseFormat.JSON)
                .frequencyPenalty(0.5)
                .build();

        // When
        UnsupportedFeatureException exception =
                assertThrows(UnsupportedFeatureException.class, () -> validate(parameters));

        // Then
        assertEquals("JSON response format, Frequency Penalty are not supported by Anthropic", exception.getMessage());
    }
}
