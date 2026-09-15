package dev.langchain4j.store.embedding.filter.comparison;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.document.Metadata;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class IsLessThanTest extends AbstractComparisonTest<IsLessThan> {

    @BeforeEach
    void setUp() {
        subject = new IsLessThan("key", 5);
    }

    @ParameterizedTest
    @CsvSource({"0, true", "4, true", "5, false", "6, false"})
    void comparisonValue(Integer value, boolean expectedResult) {
        Metadata metadata = Metadata.from(Map.of("key", value));
        assertThat(subject.test(metadata)).isEqualTo(expectedResult);
    }

    @Test
    void shouldNotThrowWhenActualValueIsUUIDAsStringAndComparisonValueIsUUID() {
        UUID uuid1 = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID uuid2 = UUID.fromString("00000000-0000-0000-0000-000000000002");
        // uuid1 < uuid2 lexicographically
        IsLessThan filter = new IsLessThan("key", uuid2);
        Metadata metadata = Metadata.from(Map.of("key", uuid1.toString()));
        assertThat(filter.test(metadata)).isTrue();
    }
}
