package dev.langchain4j.model.googleai.common;

import static dev.langchain4j.model.googleai.GeminiThinkingConfig.GeminiThinkingLevel.HIGH;
import static dev.langchain4j.model.googleai.GeminiThinkingConfig.GeminiThinkingLevel.LOW;
import static dev.langchain4j.model.googleai.GeminiThinkingConfig.GeminiThinkingLevel.MEDIUM;
import static dev.langchain4j.model.googleai.GeminiThinkingConfig.GeminiThinkingLevel.MINIMAL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.model.googleai.GeminiThinkingConfig;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "GOOGLE_AI_GEMINI_API_KEY", matches = ".+")
public class GoogleAiGeminiThinkingLevelIT {
    @Test
    void define_high_thinking_level_with_gemini3pro() {
        // Given
        GoogleAiGeminiChatModel modelHigh = GoogleAiGeminiChatModel.builder()
                .sendThinking(true)
                .returnThinking(true)
                .thinkingConfig(
                        GeminiThinkingConfig.builder().thinkingLevel(HIGH).build())
                .modelName("gemini-3-pro-preview")
                .apiKey(System.getenv("GOOGLE_AI_GEMINI_API_KEY"))
                .build();

        // When
        String responseHigh = modelHigh.chat("What is the meaning of life?");

        // Then
        assertThat(responseHigh).containsIgnoringCase("life");
    }

    @Test
    void define_low_thinking_level_with_gemini3pro() {
        // Given
        GoogleAiGeminiChatModel modelLow = GoogleAiGeminiChatModel.builder()
                .sendThinking(true)
                .returnThinking(true)
                .thinkingConfig(
                        GeminiThinkingConfig.builder().thinkingLevel(LOW).build())
                .modelName("gemini-3-pro-preview")
                .apiKey(System.getenv("GOOGLE_AI_GEMINI_API_KEY"))
                .build();

        // When
        String responseLow = modelLow.chat("What is the meaning of life?");

        // Then
        assertThat(responseLow).containsIgnoringCase("life");
    }

    @Test
    void define_minimal_thinking_level_with_gemini3flash() {
        // Given
        GoogleAiGeminiChatModel model = GoogleAiGeminiChatModel.builder()
                .sendThinking(true)
                .returnThinking(true)
                .thinkingConfig(
                        GeminiThinkingConfig.builder().thinkingLevel(MINIMAL).build())
                .modelName("gemini-3-flash-preview")
                .apiKey(System.getenv("GOOGLE_AI_GEMINI_API_KEY"))
                .build();

        // When
        String response = model.chat("What is the meaning of life?");

        // Then
        assertThat(response).containsIgnoringCase("life");
    }

    @Test
    void define_medium_thinking_level_with_gemini3flash() {
        // Given
        GoogleAiGeminiChatModel model = GoogleAiGeminiChatModel.builder()
                .sendThinking(true)
                .returnThinking(true)
                .thinkingConfig(
                        GeminiThinkingConfig.builder().thinkingLevel(MEDIUM).build())
                .modelName("gemini-3-flash-preview")
                .apiKey(System.getenv("GOOGLE_AI_GEMINI_API_KEY"))
                .build();

        // When
        String response = model.chat("What is the meaning of life?");

        // Then
        assertThat(response).containsIgnoringCase("life");
    }

    @Test
    void define_bad_thinking_level_with_gemini3pro() {
        // Given
        GoogleAiGeminiChatModel modelLow = GoogleAiGeminiChatModel.builder()
                .sendThinking(true)
                .returnThinking(true)
                .thinkingConfig(GeminiThinkingConfig.builder()
                        .thinkingLevel("DUMMY_THINKING_LEVEL")
                        .build())
                .modelName("gemini-3-pro-preview")
                .apiKey(System.getenv("GOOGLE_AI_GEMINI_API_KEY"))
                .build();

        // Then
        assertThatThrownBy(() -> modelLow.chat("What is the meaning of life?")).hasMessageContaining("Invalid value");
    }
}
