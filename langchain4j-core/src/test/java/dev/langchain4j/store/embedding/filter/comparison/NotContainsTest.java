package dev.langchain4j.store.embedding.filter.comparison;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.data.document.Metadata;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class NotContainsTest {

    @Test
    void testShouldReturnFalseWhenNotMetadata() {
        NotContains notContains = new NotContains("key", "value");
        assertThat(notContains.test("notMetadata")).isFalse();
    }

    @Test
    void testShouldReturnFalseWhenKeyNotFound() {
        NotContains notContains = new NotContains("key", "value");
        Metadata metadata = new Metadata(Map.of());
        assertThat(notContains.test(metadata)).isFalse();
    }

    @Test
    void testShouldReturnTrueWhenNotContains() {
        NotContains notContains = new NotContains("key", "value");
        Metadata metadata = new Metadata(Map.of("key", "foobar"));
        assertThat(notContains.test(metadata)).isTrue();
    }

    @Test
    void testShouldReturnFalseWhenContains() {
        NotContains notContains = new NotContains("key", "value");
        Metadata metadata = new Metadata(Map.of("key", "foovaluebar"));
        assertThat(notContains.test(metadata)).isFalse();
    }

    @Test
    void testShouldThrowIllegalArgumentExceptionWhenTypeMismatch() {
        NotContains notContains = new NotContains("key", "value");
        Metadata metadata = new Metadata(Map.of("key", 42));
        assertThatThrownBy(() -> notContains.test(metadata))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "Type mismatch: actual value of metadata key \"key\" (42) has type java.lang.Integer, while it is expected to be a string or a UUID");
    }
}
