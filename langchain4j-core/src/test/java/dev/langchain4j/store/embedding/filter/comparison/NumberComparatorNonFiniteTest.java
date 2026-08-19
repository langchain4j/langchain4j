package dev.langchain4j.store.embedding.filter.comparison;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.document.Metadata;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Metadata accepts Float and Double without excluding NaN or +/-Infinity, so every numeric
 * filter has to return a boolean for them rather than throwing.
 *
 * <p>Ordering follows {@link Double#compare}: -Infinity is the smallest value, +Infinity is
 * larger than every finite number, and NaN sorts above everything and equals itself. That is
 * Java's total order for doubles, and it matches how PostgreSQL orders float columns.
 */
class NumberComparatorNonFiniteTest {

    private static Metadata metadata(Object value) {
        Metadata metadata = new Metadata();
        if (value instanceof Double doubleValue) {
            metadata.put("score", doubleValue);
        } else {
            metadata.put("score", (Float) value);
        }
        return metadata;
    }

    // ===== No comparator throws on a non-finite value =====

    @ParameterizedTest
    @ValueSource(doubles = {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
    @DisplayName("every comparator returns a boolean for a non-finite metadata value")
    void comparators_do_not_throw_for_non_finite_metadata(double value) {
        Metadata metadata = metadata(value);

        assertThat(new IsEqualTo("score", 0.5).test(metadata)).isNotNull();
        assertThat(new IsNotEqualTo("score", 0.5).test(metadata)).isNotNull();
        assertThat(new IsGreaterThan("score", 0.5).test(metadata)).isNotNull();
        assertThat(new IsGreaterThanOrEqualTo("score", 0.5).test(metadata)).isNotNull();
        assertThat(new IsLessThan("score", 0.5).test(metadata)).isNotNull();
        assertThat(new IsLessThanOrEqualTo("score", 0.5).test(metadata)).isNotNull();
        assertThat(new IsIn("score", List.of(0.5)).test(metadata)).isNotNull();
        assertThat(new IsNotIn("score", List.of(0.5)).test(metadata)).isNotNull();
    }

    @ParameterizedTest
    @ValueSource(doubles = {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
    @DisplayName("a non-finite comparison value is handled too, not just a non-finite metadata value")
    void comparators_do_not_throw_for_non_finite_comparison_value(double value) {
        Metadata metadata = metadata(0.5);

        assertThat(new IsEqualTo("score", value).test(metadata)).isNotNull();
        assertThat(new IsGreaterThan("score", value).test(metadata)).isNotNull();
        assertThat(new IsLessThan("score", value).test(metadata)).isNotNull();
        assertThat(new IsIn("score", List.of(value)).test(metadata)).isNotNull();
    }

    @Test
    @DisplayName("Float NaN and Infinity are handled as well as Double")
    void float_non_finite_values_are_handled() {
        assertThat(new IsEqualTo("score", 0.5f).test(metadata(Float.NaN))).isFalse();
        assertThat(new IsGreaterThan("score", 0.5f).test(metadata(Float.POSITIVE_INFINITY)))
                .isTrue();
        assertThat(new IsLessThan("score", 0.5f).test(metadata(Float.NEGATIVE_INFINITY)))
                .isTrue();
    }

    // ===== Infinity keeps its natural ordering =====

    @Test
    @DisplayName("+Infinity is greater than every finite value")
    void positive_infinity_is_greater_than_finite_values() {
        Metadata metadata = metadata(Double.POSITIVE_INFINITY);

        assertThat(new IsGreaterThan("score", 0.5).test(metadata)).isTrue();
        assertThat(new IsGreaterThanOrEqualTo("score", 0.5).test(metadata)).isTrue();
        assertThat(new IsLessThan("score", 0.5).test(metadata)).isFalse();
        assertThat(new IsLessThanOrEqualTo("score", 0.5).test(metadata)).isFalse();
        assertThat(new IsEqualTo("score", 0.5).test(metadata)).isFalse();
        assertThat(new IsNotEqualTo("score", 0.5).test(metadata)).isTrue();
    }

    @Test
    @DisplayName("-Infinity is smaller than every finite value")
    void negative_infinity_is_smaller_than_finite_values() {
        Metadata metadata = metadata(Double.NEGATIVE_INFINITY);

        assertThat(new IsLessThan("score", 0.5).test(metadata)).isTrue();
        assertThat(new IsLessThanOrEqualTo("score", 0.5).test(metadata)).isTrue();
        assertThat(new IsGreaterThan("score", 0.5).test(metadata)).isFalse();
        assertThat(new IsGreaterThanOrEqualTo("score", 0.5).test(metadata)).isFalse();
    }

    @Test
    @DisplayName("an infinite value equals itself")
    void infinity_equals_itself() {
        assertThat(new IsEqualTo("score", Double.POSITIVE_INFINITY).test(metadata(Double.POSITIVE_INFINITY)))
                .isTrue();
        assertThat(new IsEqualTo("score", Double.NEGATIVE_INFINITY).test(metadata(Double.POSITIVE_INFINITY)))
                .isFalse();
        assertThat(new IsIn("score", List.of(0.5, Double.POSITIVE_INFINITY)).test(metadata(Double.POSITIVE_INFINITY)))
                .isTrue();
        assertThat(new IsNotIn("score", List.of(0.5, Double.POSITIVE_INFINITY))
                        .test(metadata(Double.POSITIVE_INFINITY)))
                .isFalse();
    }

    // ===== NaN follows Double.compare, so it equals itself and outranks everything =====

    @Test
    @DisplayName("NaN does not match a finite comparison value")
    void nan_does_not_match_a_finite_value() {
        Metadata metadata = metadata(Double.NaN);

        assertThat(new IsEqualTo("score", 0.5).test(metadata)).isFalse();
        assertThat(new IsNotEqualTo("score", 0.5).test(metadata)).isTrue();
        assertThat(new IsIn("score", List.of(0.5)).test(metadata)).isFalse();
        assertThat(new IsNotIn("score", List.of(0.5)).test(metadata)).isTrue();
    }

    @Test
    @DisplayName("NaN equals itself, matching Double.compare rather than IEEE ==")
    void nan_equals_itself() {
        Metadata metadata = metadata(Double.NaN);

        assertThat(new IsEqualTo("score", Double.NaN).test(metadata)).isTrue();
        assertThat(new IsNotEqualTo("score", Double.NaN).test(metadata)).isFalse();
        assertThat(new IsIn("score", List.of(Double.NaN)).test(metadata)).isTrue();
    }

    @Test
    @DisplayName("NaN sorts above every other value, including +Infinity")
    void nan_sorts_above_everything() {
        Metadata metadata = metadata(Double.NaN);

        assertThat(new IsGreaterThan("score", 0.5).test(metadata)).isTrue();
        assertThat(new IsGreaterThan("score", Double.POSITIVE_INFINITY).test(metadata))
                .isTrue();
        assertThat(new IsLessThan("score", Double.POSITIVE_INFINITY).test(metadata))
                .isFalse();
    }

    // ===== Guard: finite comparisons are untouched =====

    @Test
    @DisplayName("finite values still compare exactly, across mixed numeric types")
    void finite_comparisons_are_unchanged() {
        Metadata metadata = new Metadata().put("score", 1L);

        assertThat(new IsEqualTo("score", 1).test(metadata)).isTrue();
        assertThat(new IsEqualTo("score", 1.0).test(metadata)).isTrue();
        assertThat(new IsGreaterThan("score", 0).test(metadata)).isTrue();
        assertThat(new IsIn("score", List.of(1.0, 2.0)).test(metadata)).isTrue();
        assertThat(new IsNotIn("score", List.of(2.0, 3.0)).test(metadata)).isTrue();
    }

    @Test
    @DisplayName("BigDecimal precision beyond double is preserved for finite values")
    void finite_comparison_keeps_full_precision() {
        // These two longs are distinct but collapse onto the same double, so a
        // blanket switch to Double.compare would wrongly report them as equal.
        Metadata metadata = new Metadata().put("score", 9007199254740993L);

        assertThat(new IsEqualTo("score", 9007199254740992L).test(metadata)).isFalse();
        assertThat(new IsGreaterThan("score", 9007199254740992L).test(metadata)).isTrue();
    }
}
