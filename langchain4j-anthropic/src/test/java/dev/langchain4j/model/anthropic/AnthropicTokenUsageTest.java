package dev.langchain4j.model.anthropic;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.Test;

class AnthropicTokenUsageTest {

    @Test
    void should_be_equal_when_all_fields_match() {
        AnthropicTokenUsage usage1 = AnthropicTokenUsage.builder()
                .inputTokenCount(100)
                .outputTokenCount(50)
                .cacheCreationInputTokens(80)
                .cacheReadInputTokens(20)
                .build();

        AnthropicTokenUsage usage2 = AnthropicTokenUsage.builder()
                .inputTokenCount(100)
                .outputTokenCount(50)
                .cacheCreationInputTokens(80)
                .cacheReadInputTokens(20)
                .build();

        assertThat(usage1).isEqualTo(usage2);
        assertThat(usage2).isEqualTo(usage1);
        assertThat(usage1).hasSameHashCodeAs(usage2);
    }

    @Test
    void should_not_be_equal_when_cache_creation_input_tokens_differ() {
        AnthropicTokenUsage usage1 = AnthropicTokenUsage.builder()
                .inputTokenCount(100)
                .outputTokenCount(50)
                .cacheCreationInputTokens(80)
                .cacheReadInputTokens(20)
                .build();

        AnthropicTokenUsage usage2 = AnthropicTokenUsage.builder()
                .inputTokenCount(100)
                .outputTokenCount(50)
                .cacheCreationInputTokens(90)
                .cacheReadInputTokens(20)
                .build();

        assertThat(usage1).isNotEqualTo(usage2);
        assertThat(usage1.hashCode()).isNotEqualTo(usage2.hashCode());
    }

    @Test
    void should_not_be_equal_when_cache_read_input_tokens_differ() {
        AnthropicTokenUsage usage1 = AnthropicTokenUsage.builder()
                .inputTokenCount(100)
                .outputTokenCount(50)
                .cacheCreationInputTokens(80)
                .cacheReadInputTokens(20)
                .build();

        AnthropicTokenUsage usage2 = AnthropicTokenUsage.builder()
                .inputTokenCount(100)
                .outputTokenCount(50)
                .cacheCreationInputTokens(80)
                .cacheReadInputTokens(30)
                .build();

        assertThat(usage1).isNotEqualTo(usage2);
        assertThat(usage1.hashCode()).isNotEqualTo(usage2.hashCode());
    }

    @Test
    void should_not_be_equal_when_parent_fields_differ() {
        AnthropicTokenUsage usage1 = AnthropicTokenUsage.builder()
                .inputTokenCount(100)
                .outputTokenCount(50)
                .cacheCreationInputTokens(80)
                .cacheReadInputTokens(20)
                .build();

        AnthropicTokenUsage usage2 = AnthropicTokenUsage.builder()
                .inputTokenCount(200)
                .outputTokenCount(50)
                .cacheCreationInputTokens(80)
                .cacheReadInputTokens(20)
                .build();

        assertThat(usage1).isNotEqualTo(usage2);
    }

    @Test
    void should_be_equal_when_both_have_null_cache_fields() {
        AnthropicTokenUsage usage1 = AnthropicTokenUsage.builder()
                .inputTokenCount(100)
                .outputTokenCount(50)
                .cacheCreationInputTokens(null)
                .cacheReadInputTokens(null)
                .build();

        AnthropicTokenUsage usage2 = AnthropicTokenUsage.builder()
                .inputTokenCount(100)
                .outputTokenCount(50)
                .build();

        assertThat(usage1).isEqualTo(usage2);
        assertThat(usage1).hasSameHashCodeAs(usage2);
    }

    @Test
    void should_not_be_equal_when_only_one_has_null_cache_fields() {
        AnthropicTokenUsage usage1 = AnthropicTokenUsage.builder()
                .inputTokenCount(100)
                .outputTokenCount(50)
                .build();

        AnthropicTokenUsage usage2 = AnthropicTokenUsage.builder()
                .inputTokenCount(100)
                .outputTokenCount(50)
                .cacheCreationInputTokens(80)
                .cacheReadInputTokens(20)
                .build();

        assertThat(usage1).isNotEqualTo(usage2);
        assertThat(usage2).isNotEqualTo(usage1);
    }

    @Test
    void should_not_be_equal_to_parent_token_usage() {
        AnthropicTokenUsage anthropicTokenUsage = AnthropicTokenUsage.builder()
                .inputTokenCount(100)
                .outputTokenCount(50)
                .build();

        TokenUsage tokenUsage = new TokenUsage(100, 50);

        assertThat(anthropicTokenUsage).isNotEqualTo(tokenUsage);
        assertThat(tokenUsage).isNotEqualTo(anthropicTokenUsage);
    }

    @Test
    void should_be_reflexively_equal_and_not_equal_to_null() {
        AnthropicTokenUsage usage = AnthropicTokenUsage.builder()
                .inputTokenCount(100)
                .outputTokenCount(50)
                .cacheCreationInputTokens(80)
                .cacheReadInputTokens(20)
                .build();

        assertThat(usage).isEqualTo(usage);
        assertThat(usage).isNotEqualTo(null);
    }
}
