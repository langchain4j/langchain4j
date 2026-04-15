package dev.langchain4j.model.googleai;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.Test;

public class GoogleAiGeminiTokenUsageTest {
    
    @Test
    void should_add_two_basic_token_usages() {
        // given
        GoogleAiGeminiTokenUsage tokenUsage1 = GoogleAiGeminiTokenUsage.builder()
                .inputTokenCount(10)
                .outputTokenCount(20)
                .totalTokenCount(30)
                .build();

        GoogleAiGeminiTokenUsage tokenUsage2 = GoogleAiGeminiTokenUsage.builder()
                .inputTokenCount(15)
                .outputTokenCount(25)
                .totalTokenCount(40)
                .build();

        // when
        GoogleAiGeminiTokenUsage result = tokenUsage1.add(tokenUsage2);

        // then
        assertThat(result.inputTokenCount()).isEqualTo(25);
        assertThat(result.outputTokenCount()).isEqualTo(45);
        assertThat(result.totalTokenCount()).isEqualTo(70);
        assertThat(result.inputTokensDetails()).isNull();
        assertThat(result.outputTokensDetails()).isNull();
    }

    @Test
    void should_handle_null_token_usage_when_adding() {
        // given
        GoogleAiGeminiTokenUsage tokenUsage = GoogleAiGeminiTokenUsage.builder()
                .inputTokenCount(10)
                .outputTokenCount(20)
                .totalTokenCount(30)
                .build();

        // when
        GoogleAiGeminiTokenUsage result = tokenUsage.add(null);

        // then
        assertThat(result).isEqualTo(tokenUsage);
    }

    @Test
    void should_add_token_usages_with_input_details() {
        // given
        GoogleAiGeminiTokenUsage tokenUsage1 = GoogleAiGeminiTokenUsage.builder()
                .inputTokenCount(10)
                .inputTokensDetails(GoogleAiGeminiTokenUsage.InputTokensDetails.builder()
                        .cachedTokens(5)
                        .build())
                .outputTokenCount(20)
                .totalTokenCount(30)
                .build();

        GoogleAiGeminiTokenUsage tokenUsage2 = GoogleAiGeminiTokenUsage.builder()
                .inputTokenCount(15)
                .inputTokensDetails(GoogleAiGeminiTokenUsage.InputTokensDetails.builder()
                        .cachedTokens(7)
                        .build())
                .outputTokenCount(25)
                .totalTokenCount(40)
                .build();

        // when
        GoogleAiGeminiTokenUsage result = tokenUsage1.add(tokenUsage2);

        // then
        assertThat(result.inputTokenCount()).isEqualTo(25);
        assertThat(result.outputTokenCount()).isEqualTo(45);
        assertThat(result.totalTokenCount()).isEqualTo(70);
        assertThat(result.inputTokensDetails()).isNotNull();
        assertThat(result.inputTokensDetails().cachedTokens()).isEqualTo(12);
        assertThat(result.outputTokensDetails()).isNull();
    }

    @Test
    void should_add_token_usages_with_output_details() {
        // given
        GoogleAiGeminiTokenUsage tokenUsage1 = GoogleAiGeminiTokenUsage.builder()
                .inputTokenCount(10)
                .outputTokenCount(20)
                .outputTokensDetails(GoogleAiGeminiTokenUsage.OutputTokensDetails.builder()
                        .reasoningTokens(8)
                        .build())
                .totalTokenCount(30)
                .build();

        GoogleAiGeminiTokenUsage tokenUsage2 = GoogleAiGeminiTokenUsage.builder()
                .inputTokenCount(15)
                .outputTokenCount(25)
                .outputTokensDetails(GoogleAiGeminiTokenUsage.OutputTokensDetails.builder()
                        .reasoningTokens(10)
                        .build())
                .totalTokenCount(40)
                .build();

        // when
        GoogleAiGeminiTokenUsage result = tokenUsage1.add(tokenUsage2);

        // then
        assertThat(result.inputTokenCount()).isEqualTo(25);
        assertThat(result.outputTokenCount()).isEqualTo(45);
        assertThat(result.totalTokenCount()).isEqualTo(70);
        assertThat(result.inputTokensDetails()).isNull();
        assertThat(result.outputTokensDetails()).isNotNull();
        assertThat(result.outputTokensDetails().reasoningTokens()).isEqualTo(18);
    }

    @Test
    void should_add_token_usages_with_both_input_and_output_details() {
        // given
        GoogleAiGeminiTokenUsage tokenUsage1 = GoogleAiGeminiTokenUsage.builder()
                .inputTokenCount(10)
                .inputTokensDetails(GoogleAiGeminiTokenUsage.InputTokensDetails.builder()
                        .cachedTokens(5)
                        .build())
                .outputTokenCount(20)
                .outputTokensDetails(GoogleAiGeminiTokenUsage.OutputTokensDetails.builder()
                        .reasoningTokens(8)
                        .build())
                .totalTokenCount(30)
                .build();

        GoogleAiGeminiTokenUsage tokenUsage2 = GoogleAiGeminiTokenUsage.builder()
                .inputTokenCount(15)
                .inputTokensDetails(GoogleAiGeminiTokenUsage.InputTokensDetails.builder()
                        .cachedTokens(7)
                        .build())
                .outputTokenCount(25)
                .outputTokensDetails(GoogleAiGeminiTokenUsage.OutputTokensDetails.builder()
                        .reasoningTokens(10)
                        .build())
                .totalTokenCount(40)
                .build();

        // when
        GoogleAiGeminiTokenUsage result = tokenUsage1.add(tokenUsage2);

        // then
        assertThat(result.inputTokenCount()).isEqualTo(25);
        assertThat(result.outputTokenCount()).isEqualTo(45);
        assertThat(result.totalTokenCount()).isEqualTo(70);
        assertThat(result.inputTokensDetails().cachedTokens()).isEqualTo(12);
        assertThat(result.outputTokensDetails().reasoningTokens()).isEqualTo(18);
    }

    @Test
    void should_handle_one_side_having_details() {
        // given
        GoogleAiGeminiTokenUsage tokenUsage1 = GoogleAiGeminiTokenUsage.builder()
                .inputTokenCount(10)
                .inputTokensDetails(GoogleAiGeminiTokenUsage.InputTokensDetails.builder()
                        .cachedTokens(5)
                        .build())
                .outputTokenCount(20)
                .outputTokensDetails(GoogleAiGeminiTokenUsage.OutputTokensDetails.builder()
                        .reasoningTokens(8)
                        .build())
                .totalTokenCount(30)
                .build();

        GoogleAiGeminiTokenUsage tokenUsage2 = GoogleAiGeminiTokenUsage.builder()
                .inputTokenCount(15)
                .outputTokenCount(25)
                .totalTokenCount(40)
                .build();

        // when
        GoogleAiGeminiTokenUsage result = tokenUsage1.add(tokenUsage2);

        // then
        assertThat(result.inputTokenCount()).isEqualTo(25);
        assertThat(result.outputTokenCount()).isEqualTo(45);
        assertThat(result.totalTokenCount()).isEqualTo(70);
        assertThat(result.inputTokensDetails()).isNotNull();
        assertThat(result.inputTokensDetails().cachedTokens()).isEqualTo(5);
        assertThat(result.outputTokensDetails()).isNotNull();
        assertThat(result.outputTokensDetails().reasoningTokens()).isEqualTo(8);
    }

    @Test
    void should_handle_adding_standard_token_usage_to_googleAi_token_usage() {
        // given
        GoogleAiGeminiTokenUsage googleAiGeminiTokenUsage = GoogleAiGeminiTokenUsage.builder()
                .inputTokenCount(10)
                .inputTokensDetails(GoogleAiGeminiTokenUsage.InputTokensDetails.builder()
                        .cachedTokens(5)
                        .build())
                .outputTokenCount(20)
                .outputTokensDetails(GoogleAiGeminiTokenUsage.OutputTokensDetails.builder()
                        .reasoningTokens(8)
                        .build())
                .totalTokenCount(30)
                .build();
        TokenUsage tokenUsage = new TokenUsage(15, 25, 40);

        // when
        GoogleAiGeminiTokenUsage result = googleAiGeminiTokenUsage.add(tokenUsage);

        // then
        assertThat(result.inputTokenCount()).isEqualTo(25);
        assertThat(result.outputTokenCount()).isEqualTo(45);
        assertThat(result.totalTokenCount()).isEqualTo(70);
        assertThat(result.inputTokensDetails()).isNotNull();
        assertThat(result.inputTokensDetails().cachedTokens()).isEqualTo(5);
        assertThat(result.outputTokensDetails()).isNotNull();
        assertThat(result.outputTokensDetails().reasoningTokens()).isEqualTo(8);
    }

    @Test
    void should_handle_adding_googleAi_token_usage_to_standard_token_usage() {
        // given
        GoogleAiGeminiTokenUsage googleAiGeminiTokenUsage = GoogleAiGeminiTokenUsage.builder()
                .inputTokenCount(10)
                .inputTokensDetails(GoogleAiGeminiTokenUsage.InputTokensDetails.builder()
                        .cachedTokens(5)
                        .build())
                .outputTokenCount(20)
                .outputTokensDetails(GoogleAiGeminiTokenUsage.OutputTokensDetails.builder()
                        .reasoningTokens(8)
                        .build())
                .totalTokenCount(30)
                .build();

        TokenUsage tokenUsage = new TokenUsage(15, 25, 40);

        // when
        GoogleAiGeminiTokenUsage result = (GoogleAiGeminiTokenUsage) tokenUsage.add(googleAiGeminiTokenUsage);

        // then
        assertThat(result.inputTokenCount()).isEqualTo(25);
        assertThat(result.outputTokenCount()).isEqualTo(45);
        assertThat(result.totalTokenCount()).isEqualTo(70);
        assertThat(result.inputTokensDetails()).isNotNull();
        assertThat(result.inputTokensDetails().cachedTokens()).isEqualTo(5);
        assertThat(result.outputTokensDetails()).isNotNull();
        assertThat(result.outputTokensDetails().reasoningTokens()).isEqualTo(8);
    }

    @Test
    void should_handle_null_values_in_counter_fields() {
        // given
        GoogleAiGeminiTokenUsage tokenUsage1 = GoogleAiGeminiTokenUsage.builder()
                .inputTokenCount(null)
                .outputTokenCount(20)
                .totalTokenCount(null)
                .build();

        GoogleAiGeminiTokenUsage tokenUsage2 = GoogleAiGeminiTokenUsage.builder()
                .inputTokenCount(15)
                .outputTokenCount(null)
                .totalTokenCount(40)
                .build();

        // when
        GoogleAiGeminiTokenUsage result = tokenUsage1.add(tokenUsage2);

        // then
        assertThat(result.inputTokenCount()).isEqualTo(15); // null + 15 = 15
        assertThat(result.outputTokenCount()).isEqualTo(20); // 20 + null = 20
        assertThat(result.totalTokenCount()).isEqualTo(40); // null + 40 = 40
    }
}
