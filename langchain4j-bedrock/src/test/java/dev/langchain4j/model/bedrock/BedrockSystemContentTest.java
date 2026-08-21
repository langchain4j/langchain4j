package dev.langchain4j.model.bedrock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class BedrockSystemContentTest {

    // === Creation Tests ===

    @Test
    void should_create_text_content_without_cache_point() {
        BedrockSystemTextContent content = BedrockSystemTextContent.from("Hello");

        assertThat(content.text()).isEqualTo("Hello");
        assertThat(content.hasCachePoint()).isFalse();
        assertThat(content.type()).isEqualTo(BedrockSystemContentType.TEXT);
    }

    @Test
    void should_create_text_content_with_cache_point() {
        BedrockSystemTextContent content = BedrockSystemTextContent.withCachePoint("Cached content");

        assertThat(content.text()).isEqualTo("Cached content");
        assertThat(content.hasCachePoint()).isTrue();
    }

    @Test
    void should_create_text_content_via_constructor_without_cache_point() {
        BedrockSystemTextContent content = new BedrockSystemTextContent("Test text");

        assertThat(content.text()).isEqualTo("Test text");
        assertThat(content.hasCachePoint()).isFalse();
    }

    @Test
    void should_create_text_content_via_constructor_with_cache_point() {
        BedrockSystemTextContent content = new BedrockSystemTextContent("Test text", true);

        assertThat(content.text()).isEqualTo("Test text");
        assertThat(content.hasCachePoint()).isTrue();
    }

    // === Validation Tests ===

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "  ", "\t", "\n", "\r\n"})
    void should_throw_for_blank_text(String blankText) {
        assertThatThrownBy(() -> BedrockSystemTextContent.from(blankText)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void should_throw_for_text_exceeding_max_length() {
        String hugeText = "x".repeat(BedrockSystemTextContent.MAX_TEXT_LENGTH + 1);

        assertThatThrownBy(() -> BedrockSystemTextContent.from(hugeText))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("text length");
    }

    @Test
    void should_accept_text_at_max_length() {
        String maxText = "x".repeat(BedrockSystemTextContent.MAX_TEXT_LENGTH);
        BedrockSystemTextContent content = BedrockSystemTextContent.from(maxText);

        assertThat(content.text()).hasSize(BedrockSystemTextContent.MAX_TEXT_LENGTH);
    }

    // === toString Truncation Tests ===

    @Test
    void should_truncate_long_text_in_toString() {
        String longText = "x".repeat(500);
        BedrockSystemTextContent content = BedrockSystemTextContent.from(longText);

        String str = content.toString();
        assertThat(str).contains("...[500 chars]");
        assertThat(str.length()).isLessThan(400); // Truncated
    }

    @Test
    void should_not_truncate_short_text_in_toString() {
        BedrockSystemTextContent content = BedrockSystemTextContent.from("short");

        String str = content.toString();
        assertThat(str).contains("short");
        assertThat(str).doesNotContain("...");
    }

    @Test
    void should_include_cache_point_in_toString() {
        BedrockSystemTextContent withCache = BedrockSystemTextContent.withCachePoint("text");
        BedrockSystemTextContent withoutCache = BedrockSystemTextContent.from("text");

        assertThat(withCache.toString()).contains("cachePoint = true");
        assertThat(withoutCache.toString()).contains("cachePoint = false");
    }

    // === Equals, HashCode ===

    @Test
    void should_implement_equals() {
        BedrockSystemTextContent content1 = BedrockSystemTextContent.from("test");
        BedrockSystemTextContent content2 = BedrockSystemTextContent.from("test");
        BedrockSystemTextContent content3 = BedrockSystemTextContent.withCachePoint("test");
        BedrockSystemTextContent content4 = BedrockSystemTextContent.from("different");

        assertThat(content1).isEqualTo(content2);
        assertThat(content1).isNotEqualTo(content3); // Different cache point
        assertThat(content1).isNotEqualTo(content4); // Different text
        assertThat(content1).isNotEqualTo(null);
        assertThat(content1).isNotEqualTo("test"); // Different type
    }

    @Test
    void should_implement_hashcode() {
        BedrockSystemTextContent content1 = BedrockSystemTextContent.from("test");
        BedrockSystemTextContent content2 = BedrockSystemTextContent.from("test");

        assertThat(content1.hashCode()).isEqualTo(content2.hashCode());
    }

    // === Content Type Tests ===

    @Test
    void should_return_text_type() {
        BedrockSystemTextContent content = BedrockSystemTextContent.from("test");

        assertThat(content.type()).isEqualTo(BedrockSystemContentType.TEXT);
    }
}
