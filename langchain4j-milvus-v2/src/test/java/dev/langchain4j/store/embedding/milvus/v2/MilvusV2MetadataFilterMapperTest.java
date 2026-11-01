package dev.langchain4j.store.embedding.milvus.v2;

import static dev.langchain4j.store.embedding.filter.Filter.not;
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
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class MilvusV2MetadataFilterMapperTest {

    private static final MilvusServerVersion MILVUS_2 = MilvusServerVersion.from("2.6.26");
    private static final MilvusServerVersion MILVUS_3 = MilvusServerVersion.from("3.0.4");

    @Test
    void should_escape_backslash_in_string_value() {
        Filter filter = metadataKey("key").isEqualTo("a\\b");

        String expr = MilvusV2MetadataFilterMapper.map(filter, "metadata", MILVUS_2);

        assertThat(expr).isEqualTo("metadata[\"key\"] == \"a\\\\b\"");
    }

    @Test
    void should_escape_both_backslash_and_double_quote_in_string_value() {
        Filter filter = metadataKey("key").isEqualTo("a\\b\"c");

        String expr = MilvusV2MetadataFilterMapper.map(filter, "metadata", MILVUS_2);

        assertThat(expr).isEqualTo("metadata[\"key\"] == \"a\\\\b\\\"c\"");
    }

    @Test
    void should_escape_backslash_in_collection_values() {
        Filter filter = metadataKey("key").isIn("a\\b");

        String expr = MilvusV2MetadataFilterMapper.map(filter, "metadata", MILVUS_2);

        assertThat(expr).isEqualTo("metadata[\"key\"] in [\"a\\\\b\"]");
    }

    @Test
    void should_not_change_value_without_special_characters() {
        Filter filter = metadataKey("key").isEqualTo("foo");

        String expr = MilvusV2MetadataFilterMapper.map(filter, "metadata", MILVUS_2);

        assertThat(expr).isEqualTo("metadata[\"key\"] == \"foo\"");
    }

    @Test
    void should_escape_double_quote_in_key() {
        // An unescaped key would break out of the metadata["..."] accessor and inject
        // arbitrary Milvus filter expression syntax (here: an "or" term the caller never wrote).
        Filter filter = metadataKey("tenant\"] != \"\" or metadata[\"x").isEqualTo("acme");

        String expr = MilvusV2MetadataFilterMapper.map(filter, "metadata", MILVUS_2);

        assertThat(expr).isEqualTo("metadata[\"tenant\\\"] != \\\"\\\" or metadata[\\\"x\"] == \"acme\"");
    }

    @Test
    void should_escape_backslash_in_key() {
        Filter filter = metadataKey("a\\b").isEqualTo("foo");

        String expr = MilvusV2MetadataFilterMapper.map(filter, "metadata", MILVUS_2);

        assertThat(expr).isEqualTo("metadata[\"a\\\\b\"] == \"foo\"");
    }

    @Test
    void should_escape_key_in_collection_filter() {
        Filter filter = metadataKey("a\"b").isIn("foo");

        String expr = MilvusV2MetadataFilterMapper.map(filter, "metadata", MILVUS_2);

        assertThat(expr).isEqualTo("metadata[\"a\\\"b\"] in [\"foo\"]");
    }

    @Test
    void should_escape_key_in_contains_filter() {
        Filter filter = metadataKey("a\"b").containsString("foo");

        String expr = MilvusV2MetadataFilterMapper.map(filter, "metadata", MILVUS_2);

        assertThat(expr).isEqualTo("metadata[\"a\\\"b\"] LIKE \"%foo%\"");
    }

    @Test
    void should_not_change_key_without_special_characters() {
        // Metadata keys are not restricted to SQL-style identifiers, so dots, spaces and
        // non-ASCII characters must keep working exactly as before.
        Filter filter = metadataKey("user.email 1").isEqualTo("foo");

        String expr = MilvusV2MetadataFilterMapper.map(filter, "metadata", MILVUS_2);

        assertThat(expr).isEqualTo("metadata[\"user.email 1\"] == \"foo\"");
    }

    @Test
    void contains_should_wrap_plain_value_in_like_wildcards() {
        Filter filter = metadataKey("key").containsString("foo");

        String expr = MilvusV2MetadataFilterMapper.map(filter, "metadata", MILVUS_2);

        assertThat(expr).isEqualTo("metadata[\"key\"] LIKE \"%foo%\"");
    }

    @Test
    void contains_should_escape_percent_wildcard_in_value() {
        // "50%" must be matched as a literal substring; the user's '%' must NOT act as a LIKE wildcard.
        // Two backslashes reach Milvus so that one survives into the LIKE pattern.
        Filter filter = metadataKey("key").containsString("50%");

        String expr = MilvusV2MetadataFilterMapper.map(filter, "metadata", MILVUS_2);

        assertThat(expr).isEqualTo("metadata[\"key\"] LIKE \"%50\\\\%%\"");
    }

    @Test
    void contains_should_escape_underscore_wildcard_in_value() {
        // "a_b" must be matched literally; the user's '_' must NOT act as a single-character wildcard.
        Filter filter = metadataKey("key").containsString("a_b");

        String expr = MilvusV2MetadataFilterMapper.map(filter, "metadata", MILVUS_2);

        assertThat(expr).isEqualTo("metadata[\"key\"] LIKE \"%a\\\\_b%\"");
    }

    @Test
    void contains_should_escape_backslash_percent_and_underscore_together() {
        Filter filter = metadataKey("key").containsString("a\\b100%_done");

        String expr = MilvusV2MetadataFilterMapper.map(filter, "metadata", MILVUS_2);

        assertThat(expr).isEqualTo("metadata[\"key\"] LIKE \"%a\\\\b100\\\\%\\\\_done%\"");
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

        String expr = MilvusV2MetadataFilterMapper.map(filter, "metadata", MILVUS_2);

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

        assertThatThrownBy(() -> MilvusV2MetadataFilterMapper.map(filter, "metadata", MILVUS_2))
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

        assertThatThrownBy(() -> MilvusV2MetadataFilterMapper.map(filter, "metadata", MILVUS_2))
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

        assertThatThrownBy(() -> MilvusV2MetadataFilterMapper.map(filter, "metadata", MILVUS_2))
                .isExactlyInstanceOf(UnsupportedOperationException.class)
                .hasMessageStartingWith("Unsupported value type: ");
    }

    @Test
    void comparisons_outside_not_should_not_check_the_key() {
        Filter filter = metadataKey("a").isEqualTo("x").and(metadataKey("b").isGreaterThan(1));

        String expr = MilvusV2MetadataFilterMapper.map(filter, "metadata", MILVUS_2);

        assertThat(expr).isEqualTo("metadata[\"a\"] == \"x\" and metadata[\"b\"] > 1");
    }

    @Test
    void not_equal_should_also_match_entries_without_the_key() {
        Filter filter = metadataKey("key").isNotEqualTo("a");

        String expr = MilvusV2MetadataFilterMapper.map(filter, "metadata", MILVUS_2);

        assertThat(expr).isEqualTo("((not exists metadata[\"key\"]) or metadata[\"key\"] != \"a\")");
    }

    @Test
    void not_in_should_also_match_entries_without_the_key() {
        Filter filter = metadataKey("key").isNotIn("a");

        String expr = MilvusV2MetadataFilterMapper.map(filter, "metadata", MILVUS_2);

        assertThat(expr).isEqualTo("((not exists metadata[\"key\"]) or metadata[\"key\"] not in [\"a\"])");
    }

    @Test
    void not_should_require_the_key_for_the_negated_comparison() {
        Filter filter = not(metadataKey("key").isEqualTo("a"));

        String expr = MilvusV2MetadataFilterMapper.map(filter, "metadata", MILVUS_2);

        assertThat(expr).isEqualTo("not(((exists metadata[\"key\"]) and metadata[\"key\"] == \"a\"))");
    }

    @Test
    void not_should_require_the_key_for_contains() {
        Filter filter = not(metadataKey("key").containsString("foo"));

        String expr = MilvusV2MetadataFilterMapper.map(filter, "metadata", MILVUS_2);

        assertThat(expr).isEqualTo("not(((exists metadata[\"key\"]) and metadata[\"key\"] LIKE \"%foo%\"))");
    }

    @Test
    void not_should_require_the_key_in_nested_and_and_or() {
        Filter filter = not(metadataKey("a")
                .isEqualTo("x")
                .and(metadataKey("b").isGreaterThan(1).or(metadataKey("c").isNotEqualTo("y"))));

        String expr = MilvusV2MetadataFilterMapper.map(filter, "metadata", MILVUS_2);

        assertThat(expr)
                .isEqualTo("not(((exists metadata[\"a\"]) and metadata[\"a\"] == \"x\")"
                        + " and (((exists metadata[\"b\"]) and metadata[\"b\"] > 1)"
                        + " or ((not exists metadata[\"c\"]) or metadata[\"c\"] != \"y\")))");
    }

    @Test
    void double_not_should_keep_the_key_check() {
        Filter filter = not(not(metadataKey("key").isIn("a")));

        String expr = MilvusV2MetadataFilterMapper.map(filter, "metadata", MILVUS_2);

        assertThat(expr).isEqualTo("not(not(((exists metadata[\"key\"]) and metadata[\"key\"] in [\"a\"])))");
    }

    @Test
    void not_equal_inside_and_outside_not_should_match_entries_without_the_key() {
        Filter filter = metadataKey("a").isEqualTo("x").and(not(metadataKey("b").isNotEqualTo("y")));

        String expr = MilvusV2MetadataFilterMapper.map(filter, "metadata", MILVUS_2);

        assertThat(expr)
                .isEqualTo("metadata[\"a\"] == \"x\""
                        + " and not(((not exists metadata[\"b\"]) or metadata[\"b\"] != \"y\"))");
    }

    @Test
    void contains_should_escape_backslash_once_more_for_milvus_3() {
        Filter filter = metadataKey("key").containsString("back\\slash");

        assertThat(MilvusV2MetadataFilterMapper.map(filter, "metadata", MILVUS_2)).isEqualTo("metadata[\"key\"] LIKE \"%back\\\\slash%\"");
        assertThat(MilvusV2MetadataFilterMapper.map(filter, "metadata", MILVUS_3)).isEqualTo("metadata[\"key\"] LIKE \"%back\\\\\\\\slash%\"");
    }

    @Test
    void contains_should_escape_wildcards_the_same_way_for_milvus_3() {
        Filter filter = metadataKey("key").containsString("50%");

        assertThat(MilvusV2MetadataFilterMapper.map(filter, "metadata", MILVUS_3)).isEqualTo("metadata[\"key\"] LIKE \"%50\\\\%%\"");
    }

    @ParameterizedTest
    @MethodSource
    void should_check_the_key_only_where_it_changes_the_result(Filter filter, String plain, String insideNot) {
        assertThat(MilvusV2MetadataFilterMapper.map(filter, "metadata", MILVUS_2)).isEqualTo(plain);
        assertThat(MilvusV2MetadataFilterMapper.map(not(filter), "metadata", MILVUS_2)).isEqualTo(insideNot);
    }

    static Stream<Arguments> should_check_the_key_only_where_it_changes_the_result() {
        return Stream.of(
                Arguments.of(
                        metadataKey("k").containsString("v"),
                        "metadata[\"k\"] LIKE \"%v%\"",
                        "not(((exists metadata[\"k\"]) and metadata[\"k\"] LIKE \"%v%\"))"),
                Arguments.of(
                        metadataKey("k").isEqualTo("v"),
                        "metadata[\"k\"] == \"v\"",
                        "not(((exists metadata[\"k\"]) and metadata[\"k\"] == \"v\"))"),
                Arguments.of(
                        metadataKey("k").isNotEqualTo("v"),
                        "((not exists metadata[\"k\"]) or metadata[\"k\"] != \"v\")",
                        "not(((not exists metadata[\"k\"]) or metadata[\"k\"] != \"v\"))"),
                Arguments.of(
                        metadataKey("k").isGreaterThan(1),
                        "metadata[\"k\"] > 1",
                        "not(((exists metadata[\"k\"]) and metadata[\"k\"] > 1))"),
                Arguments.of(
                        metadataKey("k").isGreaterThanOrEqualTo(1),
                        "metadata[\"k\"] >= 1",
                        "not(((exists metadata[\"k\"]) and metadata[\"k\"] >= 1))"),
                Arguments.of(
                        metadataKey("k").isLessThan(1),
                        "metadata[\"k\"] < 1",
                        "not(((exists metadata[\"k\"]) and metadata[\"k\"] < 1))"),
                Arguments.of(
                        metadataKey("k").isLessThanOrEqualTo(1),
                        "metadata[\"k\"] <= 1",
                        "not(((exists metadata[\"k\"]) and metadata[\"k\"] <= 1))"),
                Arguments.of(
                        metadataKey("k").isIn("v"),
                        "metadata[\"k\"] in [\"v\"]",
                        "not(((exists metadata[\"k\"]) and metadata[\"k\"] in [\"v\"]))"),
                Arguments.of(
                        metadataKey("k").isNotIn("v"),
                        "((not exists metadata[\"k\"]) or metadata[\"k\"] not in [\"v\"])",
                        "not(((not exists metadata[\"k\"]) or metadata[\"k\"] not in [\"v\"]))"));
    }
}
