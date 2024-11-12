package dev.langchain4j.store.embedding.filter.comparison;

import dev.langchain4j.data.document.Metadata;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

 class IsEqualToTest {

    @Test
     void testShouldReturnFalseWhenNotMetadata() {
        IsEqualTo isEqualTo = new IsEqualTo("key", "value");
        assertThat(isEqualTo.test("notMetadata")).isFalse();
    }

    @Test
     void testShouldReturnFalseWhenKeyNotFound() {
        IsEqualTo isEqualTo = new IsEqualTo("key", "value");
        Metadata metadata = new Metadata(Map.of());
        assertThat(isEqualTo.test(metadata)).isFalse();
    }

    @Test
     void testShouldReturnTrueWhenValuesAreNumbers() {
        IsEqualTo isEqualTo = new IsEqualTo("key", 2);
        Metadata metadata = new Metadata(Map.of("key", 2));
        assertThat(isEqualTo.test(metadata)).isTrue();
    }

    @Test
     void testShouldReturnTrueWhenValuesAreStrings() {
        IsEqualTo isEqualTo = new IsEqualTo("key", "value");
        Metadata metadata = new Metadata(new HashMap<>() {{
            put("key", "value");
        }});
        assertThat(isEqualTo.test(metadata)).isTrue();
    }

    @Test
     void testShouldReturnTrueWhenActualValueIsUUIDAsString() {
        UUID uuid = UUID.randomUUID();
        IsEqualTo isEqualTo = new IsEqualTo("key", uuid);
        Metadata metadata = new Metadata(new HashMap<>() {{
            put("key", uuid.toString());
        }});
        assertThat(isEqualTo.test(metadata)).isTrue();
    }
}
