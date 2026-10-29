package dev.langchain4j.store.embedding.milvus;

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.comparison.IsEqualTo;
import dev.langchain4j.store.embedding.filter.comparison.IsGreaterThan;
import dev.langchain4j.store.embedding.filter.comparison.IsIn;
import dev.langchain4j.store.embedding.filter.comparison.IsLessThan;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Collection;
import java.util.List;
import org.junit.jupiter.api.Test;

class MilvusMetadataFilterMapperTest {

    @Test
    void should_not_change_value_without_special_characters() {
        Filter filter = metadataKey("key").isEqualTo("foo");

        String expr = MilvusMetadataFilterMapper.map(filter, "metadata");

        assertThat(expr).isEqualTo("metadata[\"key\"] == \"foo\"");
    }

    @Test
    void should_keep_numbers_and_booleans_as_is() {
        Filter filter = metadataKey("a")
                .isEqualTo(1)
                .and(metadataKey("b").isGreaterThan(2L))
                .and(metadataKey("c").isLessThan(1.5f))
                .and(metadataKey("d").isIn(2.5d))
                .and(new IsEqualTo("e", true))
                .and(new IsLessThan("f", new BigDecimal("9.99")))
                .and(new IsGreaterThan("g", new BigInteger("12345678901234567890")))
                .and(new IsEqualTo("h", (byte) 3))
                .and(new IsLessThan("i", (short) 4));

        String expr = MilvusMetadataFilterMapper.map(filter, "metadata");

        assertThat(expr)
                .isEqualTo("metadata[\"a\"] == 1 and metadata[\"b\"] > 2 and metadata[\"c\"] < 1.5"
                        + " and metadata[\"d\"] in [2.5] and metadata[\"e\"] == true and metadata[\"f\"] < 9.99"
                        + " and metadata[\"g\"] > 12345678901234567890 and metadata[\"h\"] == 3"
                        + " and metadata[\"i\"] < 4");
    }

    @Test
    void should_reject_unsupported_value_type() {
        // The filter classes may reject such values themselves; the subclass makes sure the mapper never
        // renders an arbitrary toString() into the expression on its own.
        Filter filter = new IsEqualTo("tenant", "placeholder") {
            @Override
            public Object comparisonValue() {
                return new StringBuilder("\"x\" or metadata[\"tenant\"] == \"bob\"");
            }
        };

        assertThatThrownBy(() -> MilvusMetadataFilterMapper.map(filter, "metadata"))
                .isExactlyInstanceOf(UnsupportedOperationException.class)
                .hasMessage("Unsupported value type: java.lang.StringBuilder");
    }

    @Test
    void should_reject_unsupported_value_type_in_collection() {
        Filter filter = new IsIn("tenant", List.of("placeholder")) {
            @Override
            public Collection<?> comparisonValues() {
                return List.of(List.of("x\"] or metadata[\"tenant\"] == \"bob"));
            }
        };

        assertThatThrownBy(() -> MilvusMetadataFilterMapper.map(filter, "metadata"))
                .isExactlyInstanceOf(UnsupportedOperationException.class)
                .hasMessageStartingWith("Unsupported value type: ");
    }

    @Test
    void should_reject_subclass_of_supported_number_type() {
        // BigDecimal is not final, so a subclass could put anything into the expression through toString().
        // The filter classes reject such values themselves; the subclass makes sure the mapper does too.
        BigDecimal value = new BigDecimal("1") {
            @Override
            public String toString() {
                return "1 or true";
            }
        };
        Filter filter = new IsEqualTo("tenant", "placeholder") {
            @Override
            public Object comparisonValue() {
                return value;
            }
        };

        assertThatThrownBy(() -> MilvusMetadataFilterMapper.map(filter, "metadata"))
                .isExactlyInstanceOf(UnsupportedOperationException.class)
                .hasMessageStartingWith("Unsupported value type: ");
    }

    @Test
    void should_escape_double_quote_in_key() {
        // An unescaped key would break out of the metadata["..."] accessor and inject
        // arbitrary Milvus filter expression syntax (here: an "or" term the caller never wrote).
        Filter filter = metadataKey("tenant\"] != \"\" or metadata[\"x").isEqualTo("acme");

        String expr = MilvusMetadataFilterMapper.map(filter, "metadata");

        assertThat(expr).isEqualTo("metadata[\"tenant\\\"] != \\\"\\\" or metadata[\\\"x\"] == \"acme\"");
    }

    @Test
    void should_escape_backslash_in_key() {
        Filter filter = metadataKey("a\\b").isEqualTo("foo");

        String expr = MilvusMetadataFilterMapper.map(filter, "metadata");

        assertThat(expr).isEqualTo("metadata[\"a\\\\b\"] == \"foo\"");
    }

    @Test
    void should_escape_key_in_collection_filter() {
        Filter filter = metadataKey("a\"b").isIn("foo");

        String expr = MilvusMetadataFilterMapper.map(filter, "metadata");

        assertThat(expr).isEqualTo("metadata[\"a\\\"b\"] in [\"foo\"]");
    }

    @Test
    void should_escape_key_in_contains_filter() {
        Filter filter = metadataKey("a\"b").containsString("foo");

        String expr = MilvusMetadataFilterMapper.map(filter, "metadata");

        assertThat(expr).isEqualTo("metadata[\"a\\\"b\"] LIKE \"%foo%\"");
    }

    @Test
    void should_not_change_key_without_special_characters() {
        // Metadata keys are not restricted to SQL-style identifiers, so dots, spaces and
        // non-ASCII characters must keep working exactly as before.
        Filter filter = metadataKey("user.email 1").isEqualTo("foo");

        String expr = MilvusMetadataFilterMapper.map(filter, "metadata");

        assertThat(expr).isEqualTo("metadata[\"user.email 1\"] == \"foo\"");
    }

    @Test
    void should_escape_backslash_at_the_end_of_a_string_value() {
        // Without escaping the backslash, it would escape the closing quote and let the rest of the
        // expression become part of the string, or let the value break out of it.
        Filter filter = metadataKey("key").isEqualTo("a\\");
        Filter breakout = metadataKey("key").isEqualTo("a\\\" or metadata[\"tenant\"] != \"");

        assertThat(MilvusMetadataFilterMapper.map(filter, "metadata")).isEqualTo("metadata[\"key\"] == \"a\\\\\"");
        assertThat(MilvusMetadataFilterMapper.map(breakout, "metadata"))
                .isEqualTo("metadata[\"key\"] == \"a\\\\\\\" or metadata[\\\"tenant\\\"] != \\\"\"");
    }
}
