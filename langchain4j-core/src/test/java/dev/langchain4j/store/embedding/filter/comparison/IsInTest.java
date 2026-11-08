package dev.langchain4j.store.embedding.filter.comparison;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

import dev.langchain4j.data.document.Metadata;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class IsInTest {

    @Test
    void shouldReturnFalseWhenNotMetadata() {
        IsIn isIn = new IsIn("key", List.of("value"));
        assertThat(isIn.test("notMetadata")).isFalse();
    }

    @Test
    void shouldReturnFalseWhenKeyNotFound() {
        IsIn isIn = new IsIn("key", List.of("value"));
        Metadata metadata = new Metadata(Map.of());
        assertThat(isIn.test(metadata)).isFalse();
    }

    @Test
    void shouldReturnTrueWhenValueIsInCollection() {
        IsIn isIn = new IsIn("key", List.of("value1", "value2"));
        Metadata metadata = new Metadata(Map.of("key", "value2"));
        assertThat(isIn.test(metadata)).isTrue();
    }

    @Test
    void shouldReturnFalseWhenValueIsNotInCollection() {
        IsIn isIn = new IsIn("key", List.of("value1", "value2"));
        Metadata metadata = new Metadata(Map.of("key", "value3"));
        assertThat(isIn.test(metadata)).isFalse();
    }

    @Test
    void shouldReturnTrueWhenActualValueIsUUIDAsString() {
        UUID uuid = UUID.randomUUID();
        IsIn isIn = new IsIn("key", List.of(uuid));
        Metadata metadata = new Metadata(Map.of("key", uuid.toString()));
        assertThat(isIn.test(metadata)).isTrue();
    }

    @Test
    void shouldReturnTrueWhenComparingDifferentNumberTypes() {
        IsIn isIn = new IsIn("key", List.of(1L));
        Metadata metadata = new Metadata(Map.of("key", 1));
        assertThat(isIn.test(metadata)).isTrue();
    }

    @Test
    void shouldBeConsistentWithIsEqualToForFloatMetadata() {
        // given: a Float metadata value and a Double comparison value that are numerically equal
        IsEqualTo isEqualTo = new IsEqualTo("key", 1.1);
        IsIn isIn = new IsIn("key", List.of(1.1));
        Metadata metadata = new Metadata(Map.of("key", 1.1f));

        // then: isIn(x) must match whenever isEqualTo(x) matches
        assertThat(isEqualTo.test(metadata)).isTrue();
        assertThat(isIn.test(metadata)).isTrue();
    }

    @Test
    void shouldReturnFalseWhenTestingNullObject() {
        IsIn isIn = new IsIn("key", List.of("value"));
        assertThat(isIn.test(null)).isFalse();
    }

    @Test
    void shouldThrowExceptionWhenComparisonValuesIsNull() {
        assertThatThrownBy(() -> new IsIn("key", null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldThrowExceptionWhenComparisonValuesIsEmpty() {
        assertThatThrownBy(() -> new IsIn("key", List.of())).isInstanceOf(IllegalArgumentException.class);
    }
}
