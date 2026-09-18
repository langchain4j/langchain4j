package dev.langchain4j.store.embedding.filter.comparison;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

import dev.langchain4j.data.document.Metadata;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class IsNotInTest {

    @Test
    void shouldReturnFalseWhenNotMetadata() {
        IsNotIn isNotIn = new IsNotIn("key", List.of("value"));
        assertThat(isNotIn.test("notMetadata")).isFalse();
    }

    @Test
    void shouldReturnTrueWhenKeyNotFound() {
        IsNotIn isNotIn = new IsNotIn("key", List.of("value"));
        Metadata metadata = new Metadata(Map.of());
        assertThat(isNotIn.test(metadata)).isTrue();
    }

    @Test
    void shouldReturnFalseWhenValueIsInCollection() {
        IsNotIn isNotIn = new IsNotIn("key", List.of("value1", "value2"));
        Metadata metadata = new Metadata(Map.of("key", "value2"));
        assertThat(isNotIn.test(metadata)).isFalse();
    }

    @Test
    void shouldReturnTrueWhenValueIsNotInCollection() {
        IsNotIn isNotIn = new IsNotIn("key", List.of("value1", "value2"));
        Metadata metadata = new Metadata(Map.of("key", "value3"));
        assertThat(isNotIn.test(metadata)).isTrue();
    }

    @Test
    void shouldBeConsistentWithIsNotEqualToForFloatMetadata() {
        // given: a Float metadata value and a Double comparison value that are numerically equal
        IsNotIn isNotIn = new IsNotIn("key", List.of(1.1));
        Metadata metadata = new Metadata(Map.of("key", 1.1f));

        // then: since isEqualTo(1.1) matches this metadata, isNotIn(1.1) must not match
        assertThat(isNotIn.test(metadata)).isFalse();
    }

    @Test
    void shouldReturnFalseWhenTestingNullObject() {
        IsNotIn isNotIn = new IsNotIn("key", List.of("value"));
        assertThat(isNotIn.test(null)).isFalse();
    }

    @Test
    void shouldThrowExceptionWhenComparisonValuesIsNull() {
        assertThatThrownBy(() -> new IsNotIn("key", null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldThrowExceptionWhenComparisonValuesIsEmpty() {
        assertThatThrownBy(() -> new IsNotIn("key", List.of())).isInstanceOf(IllegalArgumentException.class);
    }
}
