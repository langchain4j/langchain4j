package dev.langchain4j.model.openai;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.Test;

class OpenAiTokenUsageTest {

    @Test
    void should_add_two_basic_token_usages() {
        // given
        OpenAiTokenUsage tokenUsage1 = OpenAiTokenUsage.builder()
                .inputTokenCount(10)
                .outputTokenCount(20)
                .totalTokenCount(30)
                .build();

        OpenAiTokenUsage tokenUsage2 = OpenAiTokenUsage.builder()
                .inputTokenCount(15)
                .outputTokenCount(25)
                .totalTokenCount(40)
                .build();

        // when
        OpenAiTokenUsage result = tokenUsage1.add(tokenUsage2);

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
        OpenAiTokenUsage tokenUsage = OpenAiTokenUsage.builder()
                .inputTokenCount(10)
                .outputTokenCount(20)
                .totalTokenCount(30)
                .build();

        // when
        OpenAiTokenUsage result = tokenUsage.add(null);

        // then
        assertThat(result).isEqualTo(tokenUsage);
    }

    @Test
    void should_add_token_usages_with_input_details() {
        // given
        OpenAiTokenUsage tokenUsage1 = OpenAiTokenUsage.builder()
                .inputTokenCount(10)
                .inputTokensDetails(OpenAiTokenUsage.InputTokensDetails.builder()
                        .cachedTokens(5)
                        .build())
                .outputTokenCount(20)
                .totalTokenCount(30)
                .build();

        OpenAiTokenUsage tokenUsage2 = OpenAiTokenUsage.builder()
                .inputTokenCount(15)
                .inputTokensDetails(OpenAiTokenUsage.InputTokensDetails.builder()
                        .cachedTokens(7)
                        .build())
                .outputTokenCount(25)
                .totalTokenCount(40)
                .build();

        // when
        OpenAiTokenUsage result = tokenUsage1.add(tokenUsage2);

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
        OpenAiTokenUsage tokenUsage1 = OpenAiTokenUsage.builder()
                .inputTokenCount(10)
                .outputTokenCount(20)
                .outputTokensDetails(OpenAiTokenUsage.OutputTokensDetails.builder()
                        .reasoningTokens(8)
                        .build())
                .totalTokenCount(30)
                .build();

        OpenAiTokenUsage tokenUsage2 = OpenAiTokenUsage.builder()
                .inputTokenCount(15)
                .outputTokenCount(25)
                .outputTokensDetails(OpenAiTokenUsage.OutputTokensDetails.builder()
                        .reasoningTokens(10)
                        .build())
                .totalTokenCount(40)
                .build();

        // when
        OpenAiTokenUsage result = tokenUsage1.add(tokenUsage2);

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
        OpenAiTokenUsage tokenUsage1 = OpenAiTokenUsage.builder()
                .inputTokenCount(10)
                .inputTokensDetails(OpenAiTokenUsage.InputTokensDetails.builder()
                        .cachedTokens(5)
                        .build())
                .outputTokenCount(20)
                .outputTokensDetails(OpenAiTokenUsage.OutputTokensDetails.builder()
                        .reasoningTokens(8)
                        .build())
                .totalTokenCount(30)
                .build();

        OpenAiTokenUsage tokenUsage2 = OpenAiTokenUsage.builder()
                .inputTokenCount(15)
                .inputTokensDetails(OpenAiTokenUsage.InputTokensDetails.builder()
                        .cachedTokens(7)
                        .build())
                .outputTokenCount(25)
                .outputTokensDetails(OpenAiTokenUsage.OutputTokensDetails.builder()
                        .reasoningTokens(10)
                        .build())
                .totalTokenCount(40)
                .build();

        // when
        OpenAiTokenUsage result = tokenUsage1.add(tokenUsage2);

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
        OpenAiTokenUsage tokenUsage1 = OpenAiTokenUsage.builder()
                .inputTokenCount(10)
                .inputTokensDetails(OpenAiTokenUsage.InputTokensDetails.builder()
                        .cachedTokens(5)
                        .build())
                .outputTokenCount(20)
                .outputTokensDetails(OpenAiTokenUsage.OutputTokensDetails.builder()
                        .reasoningTokens(8)
                        .build())
                .totalTokenCount(30)
                .build();

        OpenAiTokenUsage tokenUsage2 = OpenAiTokenUsage.builder()
                .inputTokenCount(15)
                .outputTokenCount(25)
                .totalTokenCount(40)
                .build();

        // when
        OpenAiTokenUsage result = tokenUsage1.add(tokenUsage2);

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
    void should_handle_adding_standard_token_usage_to_openai_token_usage() {
        // given
        OpenAiTokenUsage openAiTokenUsage = OpenAiTokenUsage.builder()
                .inputTokenCount(10)
                .inputTokensDetails(OpenAiTokenUsage.InputTokensDetails.builder()
                        .cachedTokens(5)
                        .build())
                .outputTokenCount(20)
                .outputTokensDetails(OpenAiTokenUsage.OutputTokensDetails.builder()
                        .reasoningTokens(8)
                        .build())
                .totalTokenCount(30)
                .build();

        TokenUsage tokenUsage = new TokenUsage(15, 25, 40);

        // when
        OpenAiTokenUsage result = openAiTokenUsage.add(tokenUsage);

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
    void should_handle_adding_openai_token_usage_to_standard_token_usage() {
        // given
        OpenAiTokenUsage openAiTokenUsage = OpenAiTokenUsage.builder()
                .inputTokenCount(10)
                .inputTokensDetails(OpenAiTokenUsage.InputTokensDetails.builder()
                        .cachedTokens(5)
                        .build())
                .outputTokenCount(20)
                .outputTokensDetails(OpenAiTokenUsage.OutputTokensDetails.builder()
                        .reasoningTokens(8)
                        .build())
                .totalTokenCount(30)
                .build();

        TokenUsage tokenUsage = new TokenUsage(15, 25, 40);

        // when
        OpenAiTokenUsage result = (OpenAiTokenUsage) tokenUsage.add(openAiTokenUsage);

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
        OpenAiTokenUsage tokenUsage1 = OpenAiTokenUsage.builder()
                .inputTokenCount(null)
                .outputTokenCount(20)
                .totalTokenCount(null)
                .build();

        OpenAiTokenUsage tokenUsage2 = OpenAiTokenUsage.builder()
                .inputTokenCount(15)
                .outputTokenCount(null)
                .totalTokenCount(40)
                .build();

        // when
        OpenAiTokenUsage result = tokenUsage1.add(tokenUsage2);

        // then
        assertThat(result.inputTokenCount()).isEqualTo(15); // null + 15 = 15
        assertThat(result.outputTokenCount()).isEqualTo(20); // 20 + null = 20
        assertThat(result.totalTokenCount()).isEqualTo(40); // null + 40 = 40
    }

    @Test
    void should_add_cache_write_tokens() {
        // given
        OpenAiTokenUsage tokenUsage1 = OpenAiTokenUsage.builder()
                .inputTokenCount(10)
                .inputTokensDetails(OpenAiTokenUsage.InputTokensDetails.builder()
                        .cachedTokens(5)
                        .cacheWriteTokens(100)
                        .build())
                .build();

        OpenAiTokenUsage tokenUsage2 = OpenAiTokenUsage.builder()
                .inputTokenCount(15)
                .inputTokensDetails(OpenAiTokenUsage.InputTokensDetails.builder()
                        .cachedTokens(7)
                        .cacheWriteTokens(200)
                        .build())
                .build();

        // when
        OpenAiTokenUsage result = tokenUsage1.add(tokenUsage2);

        // then
        assertThat(result.inputTokensDetails().cachedTokens()).isEqualTo(12);
        assertThat(result.inputTokensDetails().cacheWriteTokens()).isEqualTo(300);
    }

    @Test
    void should_keep_cache_write_tokens_null_when_neither_side_reports_it() {
        // given
        OpenAiTokenUsage tokenUsage1 = OpenAiTokenUsage.builder()
                .inputTokensDetails(OpenAiTokenUsage.InputTokensDetails.builder()
                        .cachedTokens(5)
                        .build())
                .build();

        OpenAiTokenUsage tokenUsage2 = OpenAiTokenUsage.builder()
                .inputTokensDetails(OpenAiTokenUsage.InputTokensDetails.builder()
                        .cachedTokens(7)
                        .build())
                .build();

        // when
        OpenAiTokenUsage result = tokenUsage1.add(tokenUsage2);

        // then
        assertThat(result.inputTokensDetails().cacheWriteTokens()).isNull();
    }

    @Test
    void should_add_cache_write_tokens_when_only_one_side_reports_it() {
        // given
        OpenAiTokenUsage tokenUsage1 = OpenAiTokenUsage.builder()
                .inputTokensDetails(OpenAiTokenUsage.InputTokensDetails.builder()
                        .cacheWriteTokens(1024)
                        .build())
                .build();

        OpenAiTokenUsage tokenUsage2 = OpenAiTokenUsage.builder()
                .inputTokensDetails(OpenAiTokenUsage.InputTokensDetails.builder()
                        .cachedTokens(7)
                        .build())
                .build();

        // when
        OpenAiTokenUsage result = tokenUsage1.add(tokenUsage2);

        // then
        assertThat(result.inputTokensDetails().cacheWriteTokens()).isEqualTo(1024);
    }

    @Test
    void should_distinguish_input_token_details_by_cache_write_tokens() {
        OpenAiTokenUsage.InputTokensDetails withCacheWriteTokens = OpenAiTokenUsage.InputTokensDetails.builder()
                .cachedTokens(5)
                .cacheWriteTokens(1024)
                .build();
        OpenAiTokenUsage.InputTokensDetails withoutCacheWriteTokens =
                OpenAiTokenUsage.InputTokensDetails.builder().cachedTokens(5).build();

        assertThat(withCacheWriteTokens)
                .isNotEqualTo(withoutCacheWriteTokens)
                .doesNotHaveSameHashCodeAs(withoutCacheWriteTokens)
                .hasToString("OpenAiTokenUsage.InputTokensDetails { cachedTokens = 5, cacheWriteTokens = 1024 }");
    }
}
