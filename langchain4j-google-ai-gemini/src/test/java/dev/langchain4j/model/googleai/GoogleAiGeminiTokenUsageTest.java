package dev.langchain4j.model.googleai;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.Test;

class GoogleAiGeminiTokenUsageTest {

    @Test
    void should_add_two_basic_token_usages() {
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

        GoogleAiGeminiTokenUsage result = tokenUsage1.add(tokenUsage2);

        assertThat(result.inputTokenCount()).isEqualTo(25);
        assertThat(result.outputTokenCount()).isEqualTo(45);
        assertThat(result.totalTokenCount()).isEqualTo(70);
        assertThat(result.cachedContentTokenCount()).isNull();
        assertThat(result.thoughtsTokenCount()).isNull();
    }

    @Test
    void should_handle_null_token_usage_when_adding() {
        GoogleAiGeminiTokenUsage tokenUsage = GoogleAiGeminiTokenUsage.builder()
                .inputTokenCount(10)
                .outputTokenCount(20)
                .totalTokenCount(30)
                .cachedContentTokenCount(5)
                .thoughtsTokenCount(15)
                .build();

        GoogleAiGeminiTokenUsage result = tokenUsage.add(null);

        assertThat(result).isSameAs(tokenUsage);
    }

    @Test
    void should_add_token_usages_with_cached_and_thoughts_counts() {
        GoogleAiGeminiTokenUsage tokenUsage1 = GoogleAiGeminiTokenUsage.builder()
                .inputTokenCount(10)
                .outputTokenCount(20)
                .totalTokenCount(30)
                .cachedContentTokenCount(5)
                .thoughtsTokenCount(15)
                .build();

        GoogleAiGeminiTokenUsage tokenUsage2 = GoogleAiGeminiTokenUsage.builder()
                .inputTokenCount(1)
                .outputTokenCount(2)
                .totalTokenCount(3)
                .cachedContentTokenCount(4)
                .thoughtsTokenCount(6)
                .build();

        GoogleAiGeminiTokenUsage result = tokenUsage1.add(tokenUsage2);

        assertThat(result.inputTokenCount()).isEqualTo(11);
        assertThat(result.outputTokenCount()).isEqualTo(22);
        assertThat(result.totalTokenCount()).isEqualTo(33);
        assertThat(result.cachedContentTokenCount()).isEqualTo(9);
        assertThat(result.thoughtsTokenCount()).isEqualTo(21);
    }

    @Test
    void should_handle_one_side_having_cached_and_thoughts_counts() {
        GoogleAiGeminiTokenUsage tokenUsage1 = GoogleAiGeminiTokenUsage.builder()
                .inputTokenCount(10)
                .outputTokenCount(20)
                .totalTokenCount(30)
                .cachedContentTokenCount(5)
                .thoughtsTokenCount(15)
                .build();

        GoogleAiGeminiTokenUsage tokenUsage2 = GoogleAiGeminiTokenUsage.builder()
                .inputTokenCount(1)
                .outputTokenCount(2)
                .totalTokenCount(3)
                .build();

        GoogleAiGeminiTokenUsage result = tokenUsage1.add(tokenUsage2);

        assertThat(result.inputTokenCount()).isEqualTo(11);
        assertThat(result.outputTokenCount()).isEqualTo(22);
        assertThat(result.totalTokenCount()).isEqualTo(33);
        assertThat(result.cachedContentTokenCount()).isEqualTo(5);
        assertThat(result.thoughtsTokenCount()).isEqualTo(15);
    }

    @Test
    void should_handle_adding_standard_token_usage_to_gemini_token_usage() {
        GoogleAiGeminiTokenUsage geminiTokenUsage = GoogleAiGeminiTokenUsage.builder()
                .inputTokenCount(10)
                .outputTokenCount(20)
                .totalTokenCount(30)
                .cachedContentTokenCount(5)
                .thoughtsTokenCount(15)
                .build();

        TokenUsage tokenUsage = new TokenUsage(1, 2, 3);

        GoogleAiGeminiTokenUsage result = geminiTokenUsage.add(tokenUsage);

        assertThat(result.inputTokenCount()).isEqualTo(11);
        assertThat(result.outputTokenCount()).isEqualTo(22);
        assertThat(result.totalTokenCount()).isEqualTo(33);
        assertThat(result.cachedContentTokenCount()).isEqualTo(5);
        assertThat(result.thoughtsTokenCount()).isEqualTo(15);
    }
}
