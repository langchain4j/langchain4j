package dev.langchain4j.store.embedding.filter.comparison;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.data.document.Metadata;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ContainsTest {

    @Test
    void testShouldReturnFalseWhenNotMetadata() {
        Contains contains = new Contains("key", "value");
        assertThat(contains.test("notMetadata")).isFalse();
    }

    @Test
    void testShouldReturnFalseWhenKeyNotFound() {
        Contains contains = new Contains("key", "value");
        Metadata metadata = new Metadata(Map.of());
        assertThat(contains.test(metadata)).isFalse();
    }

    @Test
    void testShouldReturnTrueWhenContains() {
        Contains contains = new Contains("key", "value");
        Metadata metadata = new Metadata(Map.of("key", "foovaluebar"));
        assertThat(contains.test(metadata)).isTrue();
    }

    @Test
    void testShouldReturnFalseWhenNotContains() {
        Contains contains = new Contains("key", "value");
        Metadata metadata = new Metadata(Map.of("key", "foobar"));
        assertThat(contains.test(metadata)).isFalse();
    }

    @Test
    void testShouldThrowIllegalArgumentExceptionWhenTypeMismatch() {
        Contains contains = new Contains("key", "value");
        Metadata metadata = new Metadata(Map.of("key", 42));
        assertThatThrownBy(() -> contains.test(metadata))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "Type mismatch: actual value of metadata key \"key\" (42) has type java.lang.Integer, while it is expected to be a string");
    }
}
