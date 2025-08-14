package dev.langchain4j.store.embedding.filter.comparison;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.data.document.Metadata;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ContainsStringTest {

    @Test
    void shouldReturnFalseWhenNotMetadata() {
        ContainsString containsString = new ContainsString("key", "value");
        assertThat(containsString.test("notMetadata")).isFalse();
    }

    @Test
    void shouldReturnFalseWhenKeyNotFound() {
        ContainsString containsString = new ContainsString("key", "value");
        Metadata metadata = new Metadata(Map.of());
        assertThat(containsString.test(metadata)).isFalse();
    }

    @Test
    void shouldReturnTrueWhenContains() {
        ContainsString containsString = new ContainsString("key", "value");
        Metadata metadata = new Metadata(Map.of("key", "foovaluebar"));
        assertThat(containsString.test(metadata)).isTrue();
    }

    @Test
    void shouldReturnFalseWhenNotContains() {
        ContainsString containsString = new ContainsString("key", "value");
        Metadata metadata = new Metadata(Map.of("key", "foobar"));
        assertThat(containsString.test(metadata)).isFalse();
    }

    @Test
    void shouldThrowIllegalArgumentExceptionWhenTypeMismatch() {
        ContainsString containsString = new ContainsString("key", "value");
        Metadata metadata = new Metadata(Map.of("key", 42));
        assertThatThrownBy(() -> containsString.test(metadata))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "Type mismatch: actual value of metadata key \"key\" (42) has type java.lang.Integer, while it is expected to be a string");
    }
}
