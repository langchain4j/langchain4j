package dev.langchain4j.store.embedding.filter.regex;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.data.document.Metadata;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class FindTest {

    @Test
    void testShouldReturnFalseWhenNotMetadata() {
        Find find = new Find("key", Pattern.compile("regex"));
        assertThat(find.test("notMetadata")).isFalse();
    }

    @Test
    void testShouldReturnFalseWhenKeyNotFound() {
        Find find = new Find("key", Pattern.compile("regex"));
        Metadata metadata = new Metadata(Map.of());
        assertThat(find.test(metadata)).isFalse();
    }

    @Test
    void testShouldReturnTrueWhenUUIDSegmentFound() {
        UUID uuid = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");

        Find find = new Find("key", Pattern.compile("12d3-a456"));
        Metadata metadata = new Metadata(Map.of("key", uuid));
        assertThat(find.test(metadata)).isTrue();
    }

    @Test
    void testShouldReturnFalseWhenUUIDSegmentNotFound() {
        UUID uuid = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");

        Find find = new Find("key", Pattern.compile("21e2-b365"));
        Metadata metadata = new Metadata(Map.of("key", uuid));
        assertThat(find.test(metadata)).isFalse();
    }

    @Test
    void testShouldReturnTrueWhenFound() {
        Find find = new Find("key", Pattern.compile("[A-Z]{5}"));
        Metadata metadata = new Metadata(Map.of("key", "testREGEXtest"));
        assertThat(find.test(metadata)).isTrue();
    }

    @Test
    void testShouldReturnFalseWhenNotFound() {
        Find find = new Find("key", Pattern.compile("[A-Z]{5}"));
        Metadata metadata = new Metadata(Map.of("key", "testregextest"));
        assertThat(find.test(metadata)).isFalse();
    }

    @Test
    void shouldThrowIllegalArgumentExceptionWhenTypeMismatch() {
        Find find = new Find("key", Pattern.compile("[A-Z]{5}"));
        Metadata metadata = new Metadata(Map.of("key", 123));
        assertThatThrownBy(() -> find.test(metadata))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "Type mismatch: actual value of metadata key \"key\" (123) has type java.lang.Integer, while it is expected to be a string or a UUID");
    }
}
