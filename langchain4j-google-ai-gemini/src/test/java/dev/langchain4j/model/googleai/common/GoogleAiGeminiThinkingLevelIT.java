package dev.langchain4j.model.googleai.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.model.googleai.GeminiThinkingConfig;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

@EnabledIfEnvironmentVariable(named = "GOOGLE_AI_GEMINI_API_KEY", matches = ".+")
public class GoogleAiGeminiThinkingLevelIT {

    @ParameterizedTest
    @EnumSource(GeminiThinkingConfig.GeminiThinkingLevel.class)
    void should_specify_thinking_level(GeminiThinkingConfig.GeminiThinkingLevel thinkingLevel) {

        // given
        GeminiThinkingConfig config =
                GeminiThinkingConfig.builder().thinkingLevel(thinkingLevel).build();

        GoogleAiGeminiChatModel modelHigh = GoogleAiGeminiChatModel.builder()
                .apiKey(System.getenv("GOOGLE_AI_GEMINI_API_KEY"))
                .modelName("gemini-3-flash-preview")
                .thinkingConfig(config)
                .returnThinking(true)
                .sendThinking(true)
                .build();

        // when
        String responseHigh = modelHigh.chat("What is the meaning of life?");

        // then
        assertThat(responseHigh).containsIgnoringCase("life");
    }

    @Test
    void should_fail_for_invalid_thinking_level() {

        // given
        String invalidThinkingLevel = "INVALID_THINKING_LEVEL";
        GeminiThinkingConfig config = GeminiThinkingConfig.builder()
                .thinkingLevel(invalidThinkingLevel)
                .build();

        GoogleAiGeminiChatModel modelLow = GoogleAiGeminiChatModel.builder()
                .apiKey(System.getenv("GOOGLE_AI_GEMINI_API_KEY"))
                .modelName("gemini-3-flash-preview")
                .thinkingConfig(config)
                .returnThinking(true)
                .sendThinking(true)
                .build();

        // when-then
        assertThatThrownBy(() -> modelLow.chat("What is the meaning of life?")).hasMessageContaining("Invalid value");
    }

    @AfterEach
    void afterEach() throws InterruptedException {
        String ciDelaySeconds = System.getenv("CI_DELAY_SECONDS_GOOGLE_AI_GEMINI");
        if (ciDelaySeconds != null) {
            Thread.sleep(Integer.parseInt(ciDelaySeconds) * 1000L);
        }
    }
}
