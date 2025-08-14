package dev.langchain4j.store.embedding.filter.comparison;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.document.Metadata;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
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
}
