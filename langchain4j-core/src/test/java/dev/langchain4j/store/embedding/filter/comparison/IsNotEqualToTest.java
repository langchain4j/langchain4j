package dev.langchain4j.store.embedding.filter.comparison;

import dev.langchain4j.data.document.Metadata;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IsNotEqualToTest {

    @Test
    void testIsNotEqualToFilter() {
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
        assertFalse(subject.test(new Object()));
    }

    private void assertMetadataDoesNotContainKey(IsNotEqualTo subject) {
        // Testing when Metadata does not contain key
        assertTrue(subject.test(new Metadata(new HashMap<>())));
    }

    private void assertValuesAreNumbersNotEqual(String key) {
        // Testing when actual value is Number
        Metadata metadata = new Metadata(new HashMap<>());
        metadata.put(key, 123);
        IsNotEqualTo subject = new IsNotEqualTo(key, 1234);
        assertTrue(subject.test(metadata));
    }

    private void assertUuidAndStringComparison(String key) {
        // Testing when comparisonValue is instance of UUID and actualValue is instance of String
        UUID uuid = UUID.randomUUID();
        IsNotEqualTo subject = new IsNotEqualTo(key, uuid);
        Metadata metadata = new Metadata(new HashMap<>());
        metadata.put(key, uuid.toString() + "extra");
        assertTrue(subject.test(metadata));
    }

    private void assertUnequalStringValues(String key, String unequalValue) {
        // Testing when values are not equal
        IsNotEqualTo subject = new IsNotEqualTo(key, unequalValue);
        Metadata metadata = new Metadata(new HashMap<>());
        metadata.put(key, "testValue");
        assertTrue(subject.test(metadata));
    }

    private void assertEqualStringValues(String key) {
        // Testing when values are equal
        IsNotEqualTo subject = new IsNotEqualTo(key, "testValue");
        Metadata metadata = new Metadata(new HashMap<>());
        metadata.put(key, "testValue");
        assertFalse(subject.test(metadata));
    }
}
