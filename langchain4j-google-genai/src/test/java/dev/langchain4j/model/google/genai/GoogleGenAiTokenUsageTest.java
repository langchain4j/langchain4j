package dev.langchain4j.model.google.genai;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.Test;

class GoogleGenAiTokenUsageTest {

    private static GoogleGenAiTokenUsage.Builder fullyPopulated() {
        return GoogleGenAiTokenUsage.builder()
                .inputTokenCount(10)
                .outputTokenCount(20)
                .totalTokenCount(49)
                .cachedContentTokenCount(5)
                .thoughtsTokenCount(15)
                .toolUsePromptTokenCount(4);
    }

    @Test
    void should_expose_cached_content_and_thoughts_token_counts() {
        GoogleGenAiTokenUsage tokenUsage = fullyPopulated().build();

        assertThat(tokenUsage.inputTokenCount()).isEqualTo(10);
        assertThat(tokenUsage.outputTokenCount()).isEqualTo(20);
        assertThat(tokenUsage.totalTokenCount()).isEqualTo(49);
        assertThat(tokenUsage.cachedContentTokenCount()).isEqualTo(5);
        assertThat(tokenUsage.thoughtsTokenCount()).isEqualTo(15);
        assertThat(tokenUsage.toolUsePromptTokenCount()).isEqualTo(4);
    }

    @Test
    void should_leave_unset_counts_null() {
        GoogleGenAiTokenUsage tokenUsage = GoogleGenAiTokenUsage.builder()
                .inputTokenCount(10)
                .outputTokenCount(20)
                .totalTokenCount(30)
                .build();

        assertThat(tokenUsage.cachedContentTokenCount()).isNull();
        assertThat(tokenUsage.thoughtsTokenCount()).isNull();
        assertThat(tokenUsage.toolUsePromptTokenCount()).isNull();
    }

    @Test
    void should_add_cached_content_and_thoughts_token_counts() {
        GoogleGenAiTokenUsage first = fullyPopulated().build();
        GoogleGenAiTokenUsage second = GoogleGenAiTokenUsage.builder()
                .inputTokenCount(1)
                .outputTokenCount(2)
                .totalTokenCount(9)
                .cachedContentTokenCount(4)
                .thoughtsTokenCount(6)
                .toolUsePromptTokenCount(2)
                .build();

        GoogleGenAiTokenUsage result = first.add(second);

        assertThat(result.inputTokenCount()).isEqualTo(11);
        assertThat(result.outputTokenCount()).isEqualTo(22);
        assertThat(result.totalTokenCount()).isEqualTo(58);
        assertThat(result.cachedContentTokenCount()).isEqualTo(9);
        assertThat(result.thoughtsTokenCount()).isEqualTo(21);
        assertThat(result.toolUsePromptTokenCount()).isEqualTo(6);
    }

    @Test
    void should_keep_own_counts_when_adding_a_usage_without_them() {
        GoogleGenAiTokenUsage first = fullyPopulated().build();
        GoogleGenAiTokenUsage second = GoogleGenAiTokenUsage.builder()
                .inputTokenCount(1)
                .outputTokenCount(2)
                .totalTokenCount(3)
                .build();

        GoogleGenAiTokenUsage result = first.add(second);

        assertThat(result.cachedContentTokenCount()).isEqualTo(5);
        assertThat(result.thoughtsTokenCount()).isEqualTo(15);
        assertThat(result.toolUsePromptTokenCount()).isEqualTo(4);
    }

    @Test
    void should_keep_own_counts_when_adding_a_base_token_usage() {
        GoogleGenAiTokenUsage tokenUsage = fullyPopulated().build();

        GoogleGenAiTokenUsage result = tokenUsage.add(new TokenUsage(1, 2, 3));

        assertThat(result.inputTokenCount()).isEqualTo(11);
        assertThat(result.outputTokenCount()).isEqualTo(22);
        assertThat(result.totalTokenCount()).isEqualTo(52);
        assertThat(result.cachedContentTokenCount()).isEqualTo(5);
        assertThat(result.thoughtsTokenCount()).isEqualTo(15);
        assertThat(result.toolUsePromptTokenCount()).isEqualTo(4);
    }

    @Test
    void should_keep_google_gen_ai_counts_when_a_base_token_usage_is_added_to_it() {
        GoogleGenAiTokenUsage tokenUsage = fullyPopulated().build();

        TokenUsage result = new TokenUsage(1, 2, 3).add(tokenUsage);

        assertThat(result).isInstanceOf(GoogleGenAiTokenUsage.class);
        GoogleGenAiTokenUsage googleGenAiResult = (GoogleGenAiTokenUsage) result;
        assertThat(googleGenAiResult.cachedContentTokenCount()).isEqualTo(5);
        assertThat(googleGenAiResult.thoughtsTokenCount()).isEqualTo(15);
        assertThat(googleGenAiResult.toolUsePromptTokenCount()).isEqualTo(4);
    }

    @Test
    void should_return_same_instance_when_adding_null() {
        GoogleGenAiTokenUsage tokenUsage = fullyPopulated().build();

        assertThat(tokenUsage.add(null)).isSameAs(tokenUsage);
    }

    @Test
    void should_be_equal_when_all_counts_match() {
        assertThat(fullyPopulated().build())
                .isEqualTo(fullyPopulated().build())
                .hasSameHashCodeAs(fullyPopulated().build());
    }

    @Test
    void should_not_be_equal_when_cached_content_token_count_differs() {
        GoogleGenAiTokenUsage tokenUsage = fullyPopulated().build();
        GoogleGenAiTokenUsage other =
                fullyPopulated().cachedContentTokenCount(6).build();

        assertThat(tokenUsage).isNotEqualTo(other);
    }

    @Test
    void should_not_be_equal_when_thoughts_token_count_differs() {
        GoogleGenAiTokenUsage tokenUsage = fullyPopulated().build();
        GoogleGenAiTokenUsage other = fullyPopulated().thoughtsTokenCount(16).build();

        assertThat(tokenUsage).isNotEqualTo(other);
    }

    @Test
    void should_not_be_equal_when_tool_use_prompt_token_count_differs() {
        GoogleGenAiTokenUsage tokenUsage = fullyPopulated().build();
        GoogleGenAiTokenUsage other =
                fullyPopulated().toolUsePromptTokenCount(5).build();

        assertThat(tokenUsage).isNotEqualTo(other);
    }

    @Test
    void should_not_be_equal_to_a_base_token_usage_with_the_same_counts() {
        GoogleGenAiTokenUsage tokenUsage = GoogleGenAiTokenUsage.builder()
                .inputTokenCount(10)
                .outputTokenCount(20)
                .totalTokenCount(30)
                .build();

        assertThat(tokenUsage).isNotEqualTo(new TokenUsage(10, 20, 30));
    }

    @Test
    void should_include_cached_content_and_thoughts_token_counts_in_to_string() {
        assertThat(fullyPopulated().build().toString())
                .contains("cachedContentTokenCount = 5")
                .contains("thoughtsTokenCount = 15")
                .contains("toolUsePromptTokenCount = 4");
    }
}
