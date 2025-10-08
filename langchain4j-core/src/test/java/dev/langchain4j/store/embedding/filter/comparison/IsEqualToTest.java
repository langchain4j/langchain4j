package dev.langchain4j.store.embedding.filter.comparison;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

import dev.langchain4j.data.document.Metadata;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class IsEqualToTest {

    @Test
    void shouldReturnFalseWhenNotMetadata() {
        IsEqualTo isEqualTo = new IsEqualTo("key", "value");
        assertThat(isEqualTo.test("notMetadata")).isFalse();
    }

    @Test
    void shouldReturnFalseWhenKeyNotFound() {
        IsEqualTo isEqualTo = new IsEqualTo("key", "value");
        Metadata metadata = new Metadata(Map.of());
        assertThat(isEqualTo.test(metadata)).isFalse();
    }

    @Test
    void shouldReturnTrueWhenValuesAreNumbers() {
        IsEqualTo isEqualTo = new IsEqualTo("key", 2);
        Metadata metadata = new Metadata(Map.of("key", 2));
        assertThat(isEqualTo.test(metadata)).isTrue();
    }

    @Test
    void shouldReturnTrueWhenValuesAreStrings() {
        IsEqualTo isEqualTo = new IsEqualTo("key", "value");
        Metadata metadata = new Metadata(new HashMap<>() {
            {
                put("key", "value");
            }
        });
        assertThat(isEqualTo.test(metadata)).isTrue();
    }

    @Test
    void shouldReturnTrueWhenActualValueIsUUIDAsString() {
        UUID uuid = UUID.randomUUID();
        IsEqualTo isEqualTo = new IsEqualTo("key", uuid);
        Metadata metadata = new Metadata(new HashMap<>() {
            {
                put("key", uuid.toString());
            }
        });
        assertThat(isEqualTo.test(metadata)).isTrue();
    }

    @Test
    void shouldReturnFalseWhenValuesAreDifferent() {
        IsEqualTo isEqualTo = new IsEqualTo("key", "value1");
        Metadata metadata = new Metadata(Map.of("key", "value2"));
        assertThat(isEqualTo.test(metadata)).isFalse();
    }

    @Test
    void shouldReturnTrueWhenComparingDifferentNumberTypes() {
        IsEqualTo isEqualTo = new IsEqualTo("key", 1L);
        Metadata metadata = new Metadata(Map.of("key", 1));
        assertThat(isEqualTo.test(metadata)).isTrue();
    }

    @Test
    void shouldHandleCaseSensitiveStringComparison() {
        IsEqualTo isEqualTo = new IsEqualTo("key", "Value");
        Metadata metadata = new Metadata(Map.of("key", "value"));
        assertThat(isEqualTo.test(metadata)).isFalse();
    }

    @Test
    void shouldReturnFalseWhenTestingNullObject() {
        IsEqualTo isEqualTo = new IsEqualTo("key", "value");
        assertThat(isEqualTo.test(null)).isFalse();
    }

    @Test
    void shouldThrowExceptionWhenComparisonValueIsNull() {
        assertThatThrownBy(() -> new IsEqualTo("key", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("comparisonValue with key 'key' cannot be null");
    }

    @Test
    void shouldHandleKeyWithSpecialCharacters() {
        IsEqualTo isEqualTo = new IsEqualTo("key.with.dots", "value");
        Metadata metadata = new Metadata(Map.of("key.with.dots", "value"));
        assertThat(isEqualTo.test(metadata)).isTrue();
    }

    @Test
    void shouldHandleFloatingPointComparison() {
        IsEqualTo isEqualTo = new IsEqualTo("key", 0.1);
        Metadata metadata = new Metadata(Map.of("key", 0.1));
        assertThat(isEqualTo.test(metadata)).isTrue();
    }
}
