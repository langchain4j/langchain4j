package dev.langchain4j.store.embedding.filter.comparison;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.document.Metadata;
import java.util.HashMap;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class IsNotEqualToTest {

    @Test
    void isNotEqualToFilter() {
        String key = "testKey";
        String unequalValue = "notEqual";

        IsNotEqualTo subject = new IsNotEqualTo(key, "testValue");

        assertIsNotEqualToObject(subject);
        assertMetadataDoesNotContainKey(subject);
        assertValuesAreNumbersNotEqual(key);
        assertUuidAndStringComparison(key);
        assertUnequalStringValues(key, unequalValue);
        assertEqualStringValues(key);
    }

    private void assertIsNotEqualToObject(IsNotEqualTo subject) {
        // Testing when object is not an instance of Metadata
        assertThat(subject.test(new Object())).isFalse();
    }

    private void assertMetadataDoesNotContainKey(IsNotEqualTo subject) {
        // Testing when Metadata does not contain key
        assertThat(subject.test(new Metadata(new HashMap<>()))).isTrue();
    }

    private void assertValuesAreNumbersNotEqual(String key) {
        // Testing when actual value is Number
        Metadata metadata = new Metadata(new HashMap<>());
        metadata.put(key, 123);
        IsNotEqualTo subject = new IsNotEqualTo(key, 1234);
        assertThat(subject.test(metadata)).isTrue();
    }

    private void assertUuidAndStringComparison(String key) {
        // Testing when comparisonValue is instance of UUID and actualValue is instance of String
        UUID uuid = UUID.randomUUID();
        IsNotEqualTo subject = new IsNotEqualTo(key, uuid);
        Metadata metadata = new Metadata(new HashMap<>());
        metadata.put(key, uuid.toString() + "extra");
        assertThat(subject.test(metadata)).isTrue();
    }

    private void assertUnequalStringValues(String key, String unequalValue) {
        // Testing when values are not equal
        IsNotEqualTo subject = new IsNotEqualTo(key, unequalValue);
        Metadata metadata = new Metadata(new HashMap<>());
        metadata.put(key, "testValue");
        assertThat(subject.test(metadata)).isTrue();
    }

    private void assertEqualStringValues(String key) {
        // Testing when values are equal
        IsNotEqualTo subject = new IsNotEqualTo(key, "testValue");
        Metadata metadata = new Metadata(new HashMap<>());
        metadata.put(key, "testValue");
        assertThat(subject.test(metadata)).isFalse();
    }
}
