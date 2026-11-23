package dev.langchain4j.store.embedding.filter.comparison;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.document.Metadata;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class IsGreaterThanTest extends AbstractComparisonTest<IsGreaterThan> {

    @BeforeEach
    void beforeEach() {
        subject = new IsGreaterThan("key", 5);
    }

    @ParameterizedTest
    @CsvSource({"0, false", "4, false", "5, false", "6, true"})
    void comparisonValue(Integer value, boolean expectedResult) {
        Metadata metadata = Metadata.from(Map.of("key", value));
        assertThat(subject.test(metadata)).isEqualTo(expectedResult);
    }

    @Test
    void shouldCompareUuidStoredAsStringConsistentlyWithIsEqualTo() {
        // The value stored in metadata is the string representation of a UUID, and the
        // comparison value is a UUID. IsEqualTo treats these as equal, so a strict range
        // filter over the same stored value must not throw and must not report greater/less.
        UUID uuid = UUID.randomUUID();
        Metadata metadata = new Metadata(new HashMap<>() {
            {
                put("key", uuid.toString());
            }
        });

        assertThat(new IsGreaterThan("key", uuid).test(metadata)).isFalse();
    }
}
