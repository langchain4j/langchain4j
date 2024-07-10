package dev.langchain4j.store.embedding.oracle;

import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.MetadataFilterBuilder;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

public class OracleJSONPathFilterMapperTest {
    final OracleJSONPathFilterMapper mapper = new OracleJSONPathFilterMapper();

    static String query(String q) {
        return String.format("where json_exists(metadata, '$?(%s)')", q);
    }

    public static Stream<Arguments> filters() {
        return Stream.of(
                Arguments.of(Filter.not(MetadataFilterBuilder.metadataKey("key").isEqualTo("abc")), query("!(@.key == \"abc\")")),
                Arguments.of(MetadataFilterBuilder.metadataKey("key").isNotIn(1, 2, 3), query("!(@.key in (1,2,3))")),
                Arguments.of(MetadataFilterBuilder.metadataKey("key").isIn(1, 2, 3), query("@.key in (1,2,3)")),
                Arguments.of(MetadataFilterBuilder.metadataKey("key").isLessThanOrEqualTo(1).and(MetadataFilterBuilder.metadataKey("key").isGreaterThanOrEqualTo(-1)), query("@.key <= 1 && @.key >= -1")),
                Arguments.of(MetadataFilterBuilder.metadataKey("key").isGreaterThan(1).and(MetadataFilterBuilder.metadataKey("key").isLessThan(2)), query("@.key > 1 && @.key < 2")),
                Arguments.of(MetadataFilterBuilder.metadataKey("key").isEqualTo(1), query("@.key == 1")),
                Arguments.of(MetadataFilterBuilder.metadataKey("key").isEqualTo("value"), query("@.key == \"value\""))
        );
    }

    @ParameterizedTest
    @MethodSource("filters")
    void testMetadataFilter(Filter filter, String clause) {
        assertThat(mapper.whereClause(filter)).isEqualTo(clause);
    }
}
