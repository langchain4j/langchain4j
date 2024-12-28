package dev.langchain4j.store.embedding.filter.regex;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.data.document.Metadata;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class MatchesTest {

    @Test
    void testShouldReturnFalseWhenNotMetadata() {
        Matches matches = new Matches("key", Pattern.compile("regex"));
        assertThat(matches.test("notMetadata")).isFalse();
    }

    @Test
    void testShouldReturnFalseWhenKeyNotFound() {
        Matches matches = new Matches("key", Pattern.compile("regex"));
        Metadata metadata = new Metadata(Map.of());
        assertThat(matches.test(metadata)).isFalse();
    }

    @Test
    void testShouldReturnTrueWhenUUIDMatches() {
        UUID uuid = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");

        Find matches = new Find("key", Pattern.compile(uuid.toString()));
        Metadata metadata = new Metadata(Map.of("key", uuid));
        assertThat(matches.test(metadata)).isTrue();
    }

    @Test
    void testShouldReturnFalseWhenUUIDNotMatches() {
        UUID uuid = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");

        Find matches = new Find("key", Pattern.compile(uuid.toString().replace("2", "4")));
        Metadata metadata = new Metadata(Map.of("key", uuid));
        assertThat(matches.test(metadata)).isFalse();
    }

    @Test
    void testShouldReturnTrueWhenMatch() {
        Matches matches = new Matches("key", Pattern.compile("[A-Z]{5}"));
        Metadata metadata = new Metadata(Map.of("key", "REGEX"));
        assertThat(matches.test(metadata)).isTrue();
    }

    @Test
    void testShouldReturnFalseWhenNoMatch() {
        Matches matches = new Matches("key", Pattern.compile("[A-Z]{5}"));
        Metadata metadata = new Metadata(Map.of("key", "testREGEXtest"));
        assertThat(matches.test(metadata)).isFalse();
    }

    @Test
    void shouldThrowIllegalArgumentExceptionWhenTypeMismatch() {
        Matches matches = new Matches("key", Pattern.compile("regex"));
        Metadata metadata = new Metadata(Map.of("key", 123));
        assertThatThrownBy(() -> matches.test(metadata))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(
                        "Type mismatch: actual value of metadata key \"key\" (123) has type java.lang.Integer, while it is expected to be a string or a UUID");
    }
}
