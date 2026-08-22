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

class IsGreaterThanOrEqualToTest extends AbstractComparisonTest<IsGreaterThanOrEqualTo> {

    @BeforeEach
    void setUp() {
        subject = new IsGreaterThanOrEqualTo("key", 5);
    }

    @ParameterizedTest
    @CsvSource({"4, false", "5, true", "6, true"})
    void comparisonValue(Integer value, boolean expectedResult) {
        Metadata metadata = Metadata.from(Map.of("key", value));
        assertThat(subject.test(metadata)).isEqualTo(expectedResult);
    }

    @Test
    void shouldCompareUuidStoredAsStringConsistentlyWithIsEqualTo() {
        // An equal UUID comparison value against a string-stored UUID metadata value
        // must not throw and is treated as equal (>= reports true).
        UUID uuid = UUID.randomUUID();
        Metadata metadata = new Metadata(new HashMap<>() {
            {
                put("key", uuid.toString());
            }
        });

        assertThat(new IsGreaterThanOrEqualTo("key", uuid).test(metadata)).isTrue();
    }
}
