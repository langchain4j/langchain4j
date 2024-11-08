package dev.langchain4j.store.embedding.filter.comparison;

import dev.langchain4j.data.document.Metadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class IsLessThanTest extends AbstractComparisonTest<IsLessThan> {

    @BeforeEach
    void setUp() {
        subject = new IsLessThan("key", 5);
    }

    @ParameterizedTest
    @CsvSource({
        "0, true",
        "4, true",
        "5, false",
        "6, false"
    })
    void testComparisonValue(Integer value, boolean expectedResult) {
        Metadata metadata = Metadata.from(Map.of("key", value));
        assertThat(subject.test(metadata)).isEqualTo(expectedResult);
    }

}
