package dev.langchain4j.model.bedrock;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.Test;

/**
 * Tests for BedrockTokenUsage, including equals/hashCode contract.
 */
class BedrockTokenUsageTest {

    @Test
    void should_create_with_all_fields() {
        BedrockTokenUsage usage = BedrockTokenUsage.builder()
                .inputTokenCount(100)
                .outputTokenCount(50)
                .cacheWriteInputTokens(80)
                .cacheReadInputTokens(20)
                .build();

        assertThat(usage.inputTokenCount()).isEqualTo(100);
        assertThat(usage.outputTokenCount()).isEqualTo(50);
        assertThat(usage.totalTokenCount()).isEqualTo(150);
        assertThat(usage.cacheWriteInputTokens()).isEqualTo(80);
        assertThat(usage.cacheReadInputTokens()).isEqualTo(20);
    }

    @Test
    void should_create_with_null_cache_fields() {
        BedrockTokenUsage usage = BedrockTokenUsage.builder()
                .inputTokenCount(100)
                .outputTokenCount(50)
                .build();

        assertThat(usage.inputTokenCount()).isEqualTo(100);
        assertThat(usage.outputTokenCount()).isEqualTo(50);
        assertThat(usage.cacheWriteInputTokens()).isNull();
        assertThat(usage.cacheReadInputTokens()).isNull();
    }

    // === Equals Tests ===

    @Test
    void should_be_equal_when_all_fields_match() {
        BedrockTokenUsage usage1 = BedrockTokenUsage.builder()
                .inputTokenCount(100)
                .outputTokenCount(50)
                .cacheWriteInputTokens(80)
                .cacheReadInputTokens(20)
                .build();

        BedrockTokenUsage usage2 = BedrockTokenUsage.builder()
                .inputTokenCount(100)
                .outputTokenCount(50)
                .cacheWriteInputTokens(80)
                .cacheReadInputTokens(20)
                .build();

        assertThat(usage1).isEqualTo(usage2);
        assertThat(usage2).isEqualTo(usage1);
    }

    @Test
    void should_not_be_equal_when_cache_write_differs() {
        BedrockTokenUsage usage1 = BedrockTokenUsage.builder()
                .inputTokenCount(100)
                .outputTokenCount(50)
                .cacheWriteInputTokens(80)
                .cacheReadInputTokens(20)
                .build();

        BedrockTokenUsage usage2 = BedrockTokenUsage.builder()
                .inputTokenCount(100)
                .outputTokenCount(50)
                .cacheWriteInputTokens(90) // Different
                .cacheReadInputTokens(20)
                .build();

        assertThat(usage1).isNotEqualTo(usage2);
    }

    @Test
    void should_not_be_equal_when_cache_read_differs() {
        BedrockTokenUsage usage1 = BedrockTokenUsage.builder()
                .inputTokenCount(100)
                .outputTokenCount(50)
                .cacheWriteInputTokens(80)
                .cacheReadInputTokens(20)
                .build();

        BedrockTokenUsage usage2 = BedrockTokenUsage.builder()
                .inputTokenCount(100)
                .outputTokenCount(50)
                .cacheWriteInputTokens(80)
                .cacheReadInputTokens(30) // Different
                .build();

        assertThat(usage1).isNotEqualTo(usage2);
    }

    @Test
    void should_not_be_equal_when_parent_fields_differ() {
        BedrockTokenUsage usage1 = BedrockTokenUsage.builder()
                .inputTokenCount(100)
                .outputTokenCount(50)
                .cacheWriteInputTokens(80)
                .cacheReadInputTokens(20)
                .build();

        BedrockTokenUsage usage2 = BedrockTokenUsage.builder()
                .inputTokenCount(200) // Different
                .outputTokenCount(50)
                .cacheWriteInputTokens(80)
                .cacheReadInputTokens(20)
                .build();

        assertThat(usage1).isNotEqualTo(usage2);
    }

    @Test
    void should_handle_null_cache_fields_in_equality() {
        BedrockTokenUsage usage1 = BedrockTokenUsage.builder()
                .inputTokenCount(100)
                .outputTokenCount(50)
                .cacheWriteInputTokens(null)
                .cacheReadInputTokens(null)
                .build();

        BedrockTokenUsage usage2 = BedrockTokenUsage.builder()
                .inputTokenCount(100)
                .outputTokenCount(50)
                .build();

        assertThat(usage1).isEqualTo(usage2);
    }

    @Test
    void should_not_be_equal_when_one_has_null_cache_write() {
        BedrockTokenUsage usage1 = BedrockTokenUsage.builder()
                .inputTokenCount(100)
                .outputTokenCount(50)
                .cacheWriteInputTokens(null)
                .cacheReadInputTokens(20)
                .build();

        BedrockTokenUsage usage2 = BedrockTokenUsage.builder()
                .inputTokenCount(100)
                .outputTokenCount(50)
                .cacheWriteInputTokens(80)
                .cacheReadInputTokens(20)
                .build();

        assertThat(usage1).isNotEqualTo(usage2);
    }

    @Test
    void should_be_reflexively_equal() {
        BedrockTokenUsage usage = BedrockTokenUsage.builder()
                .inputTokenCount(100)
                .outputTokenCount(50)
                .cacheWriteInputTokens(80)
                .cacheReadInputTokens(20)
                .build();

        assertThat(usage).isEqualTo(usage);
    }

    @Test
    void should_not_be_equal_to_null() {
        BedrockTokenUsage usage = BedrockTokenUsage.builder()
                .inputTokenCount(100)
                .outputTokenCount(50)
                .build();

        assertThat(usage).isNotEqualTo(null);
    }

    @Test
    void should_not_be_equal_to_parent_class() {
        BedrockTokenUsage bedrockUsage = BedrockTokenUsage.builder()
                .inputTokenCount(100)
                .outputTokenCount(50)
                .build();

        TokenUsage parentUsage = new TokenUsage(100, 50);

        // Different classes should not be equal
        assertThat(bedrockUsage).isNotEqualTo(parentUsage);
        assertThat(parentUsage).isNotEqualTo(bedrockUsage);
    }

    // === HashCode Tests ===

    @Test
    void should_have_same_hashcode_when_equal() {
        BedrockTokenUsage usage1 = BedrockTokenUsage.builder()
                .inputTokenCount(100)
                .outputTokenCount(50)
                .cacheWriteInputTokens(80)
                .cacheReadInputTokens(20)
                .build();

        BedrockTokenUsage usage2 = BedrockTokenUsage.builder()
                .inputTokenCount(100)
                .outputTokenCount(50)
                .cacheWriteInputTokens(80)
                .cacheReadInputTokens(20)
                .build();

        assertThat(usage1.hashCode()).isEqualTo(usage2.hashCode());
    }

    @Test
    void should_have_consistent_hashcode() {
        BedrockTokenUsage usage = BedrockTokenUsage.builder()
                .inputTokenCount(100)
                .outputTokenCount(50)
                .cacheWriteInputTokens(80)
                .cacheReadInputTokens(20)
                .build();

        int hashCode1 = usage.hashCode();
        int hashCode2 = usage.hashCode();

        assertThat(hashCode1).isEqualTo(hashCode2);
    }

    @Test
    void should_have_different_hashcode_when_cache_fields_differ() {
        BedrockTokenUsage usage1 = BedrockTokenUsage.builder()
                .inputTokenCount(100)
                .outputTokenCount(50)
                .cacheWriteInputTokens(80)
                .cacheReadInputTokens(20)
                .build();

        BedrockTokenUsage usage2 = BedrockTokenUsage.builder()
                .inputTokenCount(100)
                .outputTokenCount(50)
                .cacheWriteInputTokens(90) // Different
                .cacheReadInputTokens(20)
                .build();

        // Note: Different objects are allowed to have the same hash code,
        // but it's a good sign if they're different for better hash distribution
        assertThat(usage1.hashCode()).isNotEqualTo(usage2.hashCode());
    }

    // === Add Method Tests ===

    @Test
    void should_add_two_bedrock_token_usages() {
        BedrockTokenUsage usage1 = BedrockTokenUsage.builder()
                .inputTokenCount(100)
                .outputTokenCount(50)
                .cacheWriteInputTokens(80)
                .cacheReadInputTokens(20)
                .build();

        BedrockTokenUsage usage2 = BedrockTokenUsage.builder()
                .inputTokenCount(200)
                .outputTokenCount(100)
                .cacheWriteInputTokens(40)
                .cacheReadInputTokens(10)
                .build();

        BedrockTokenUsage result = usage1.add(usage2);

        assertThat(result.inputTokenCount()).isEqualTo(300);
        assertThat(result.outputTokenCount()).isEqualTo(150);
        assertThat(result.cacheWriteInputTokens()).isEqualTo(120);
        assertThat(result.cacheReadInputTokens()).isEqualTo(30);
    }

    @Test
    void should_add_with_null_cache_fields() {
        BedrockTokenUsage usage1 = BedrockTokenUsage.builder()
                .inputTokenCount(100)
                .outputTokenCount(50)
                .cacheWriteInputTokens(80)
                .cacheReadInputTokens(null)
                .build();

        BedrockTokenUsage usage2 = BedrockTokenUsage.builder()
                .inputTokenCount(200)
                .outputTokenCount(100)
                .cacheWriteInputTokens(null)
                .cacheReadInputTokens(10)
                .build();

        BedrockTokenUsage result = usage1.add(usage2);

        assertThat(result.inputTokenCount()).isEqualTo(300);
        assertThat(result.outputTokenCount()).isEqualTo(150);
        assertThat(result.cacheWriteInputTokens()).isEqualTo(80);
        assertThat(result.cacheReadInputTokens()).isEqualTo(10);
    }

    @Test
    void should_add_with_null_argument() {
        BedrockTokenUsage usage = BedrockTokenUsage.builder()
                .inputTokenCount(100)
                .outputTokenCount(50)
                .cacheWriteInputTokens(80)
                .cacheReadInputTokens(20)
                .build();

        BedrockTokenUsage result = usage.add(null);

        assertThat(result).isSameAs(usage);
    }

    @Test
    void should_add_bedrock_to_parent_token_usage() {
        BedrockTokenUsage bedrockUsage = BedrockTokenUsage.builder()
                .inputTokenCount(100)
                .outputTokenCount(50)
                .cacheWriteInputTokens(80)
                .cacheReadInputTokens(20)
                .build();

        TokenUsage parentUsage = new TokenUsage(200, 100);

        BedrockTokenUsage result = bedrockUsage.add(parentUsage);

        assertThat(result.inputTokenCount()).isEqualTo(300);
        assertThat(result.outputTokenCount()).isEqualTo(150);
        // Cache fields preserved from bedrock usage only
        assertThat(result.cacheWriteInputTokens()).isEqualTo(80);
        assertThat(result.cacheReadInputTokens()).isEqualTo(20);
    }

    // === ToString Tests ===

    @Test
    void should_include_all_fields_in_toString() {
        BedrockTokenUsage usage = BedrockTokenUsage.builder()
                .inputTokenCount(100)
                .outputTokenCount(50)
                .cacheWriteInputTokens(80)
                .cacheReadInputTokens(20)
                .build();

        String str = usage.toString();

        assertThat(str).contains("inputTokenCount = 100");
        assertThat(str).contains("outputTokenCount = 50");
        assertThat(str).contains("totalTokenCount = 150");
        assertThat(str).contains("cacheWriteInputTokens = 80");
        assertThat(str).contains("cacheReadInputTokens = 20");
    }
}
