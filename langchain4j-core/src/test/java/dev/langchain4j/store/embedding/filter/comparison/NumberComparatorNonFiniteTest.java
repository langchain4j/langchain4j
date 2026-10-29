package dev.langchain4j.store.embedding.filter.comparison;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import dev.langchain4j.data.document.Metadata;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * {@link Metadata} accepts {@link Float} and {@link Double} without excluding infinity and NaN, so numeric
 * filters have to return a boolean for those stored values instead of throwing. Comparison values themselves
 * are always finite, the filters reject anything else.
 *
 * <p>Infinities keep their natural ordering. NaN is not comparable to anything, not even to itself, so every
 * comparison involving it is false and only the negated filters match.
 */
class NumberComparatorNonFiniteTest {

    private static final double FINITE = 0.5;

    @ParameterizedTest
    @ValueSource(doubles = {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
    void shouldNotThrowForNonFiniteMetadataValue(double value) {
        Metadata metadata = metadata(value);

        assertThatCode(() -> {
                    new IsEqualTo("score", FINITE).test(metadata);
                    new IsNotEqualTo("score", FINITE).test(metadata);
                    new IsGreaterThan("score", FINITE).test(metadata);
                    new IsGreaterThanOrEqualTo("score", FINITE).test(metadata);
                    new IsLessThan("score", FINITE).test(metadata);
                    new IsLessThanOrEqualTo("score", FINITE).test(metadata);
                    new IsIn("score", List.of(FINITE)).test(metadata);
                    new IsNotIn("score", List.of(FINITE)).test(metadata);
                })
                .doesNotThrowAnyException();
    }

    @Test
    void shouldTreatPositiveInfinityAsGreaterThanFiniteValues() {
        Metadata metadata = metadata(Double.POSITIVE_INFINITY);

        assertThat(new IsGreaterThan("score", FINITE).test(metadata)).isTrue();
        assertThat(new IsGreaterThanOrEqualTo("score", FINITE).test(metadata)).isTrue();
        assertThat(new IsLessThan("score", FINITE).test(metadata)).isFalse();
        assertThat(new IsLessThanOrEqualTo("score", FINITE).test(metadata)).isFalse();
        assertThat(new IsEqualTo("score", FINITE).test(metadata)).isFalse();
        assertThat(new IsNotEqualTo("score", FINITE).test(metadata)).isTrue();
    }

    @Test
    void shouldTreatNegativeInfinityAsSmallerThanFiniteValues() {
        Metadata metadata = metadata(Double.NEGATIVE_INFINITY);

        assertThat(new IsLessThan("score", FINITE).test(metadata)).isTrue();
        assertThat(new IsLessThanOrEqualTo("score", FINITE).test(metadata)).isTrue();
        assertThat(new IsGreaterThan("score", FINITE).test(metadata)).isFalse();
        assertThat(new IsGreaterThanOrEqualTo("score", FINITE).test(metadata)).isFalse();
        assertThat(new IsEqualTo("score", FINITE).test(metadata)).isFalse();
        assertThat(new IsNotEqualTo("score", FINITE).test(metadata)).isTrue();
    }

    @Test
    void shouldOrderInfinityBeyondLargestFiniteValues() {
        assertThat(new IsGreaterThan("score", Double.MAX_VALUE).test(metadata(Double.POSITIVE_INFINITY)))
                .isTrue();
        assertThat(new IsLessThan("score", -Double.MAX_VALUE).test(metadata(Double.NEGATIVE_INFINITY)))
                .isTrue();
        assertThat(new IsIn("score", List.of(FINITE, Double.MAX_VALUE)).test(metadata(Double.POSITIVE_INFINITY)))
                .isFalse();
        assertThat(new IsNotIn("score", List.of(FINITE, Double.MAX_VALUE)).test(metadata(Double.POSITIVE_INFINITY)))
                .isTrue();
    }

    @Test
    void shouldNotMatchNaNMetadataValue() {
        Metadata metadata = metadata(Double.NaN);

        assertThat(new IsEqualTo("score", FINITE).test(metadata)).isFalse();
        assertThat(new IsGreaterThan("score", FINITE).test(metadata)).isFalse();
        assertThat(new IsGreaterThanOrEqualTo("score", FINITE).test(metadata)).isFalse();
        assertThat(new IsLessThan("score", FINITE).test(metadata)).isFalse();
        assertThat(new IsLessThanOrEqualTo("score", FINITE).test(metadata)).isFalse();
        assertThat(new IsIn("score", List.of(FINITE)).test(metadata)).isFalse();

        assertThat(new IsNotEqualTo("score", FINITE).test(metadata)).isTrue();
        assertThat(new IsNotIn("score", List.of(FINITE)).test(metadata)).isTrue();
    }

    @Test
    void shouldNotOrderNaNAgainstLargestFiniteValues() {
        Metadata metadata = metadata(Double.NaN);

        assertThat(new IsGreaterThan("score", -Double.MAX_VALUE).test(metadata)).isFalse();
        assertThat(new IsLessThan("score", Double.MAX_VALUE).test(metadata)).isFalse();
        assertThat(new IsGreaterThanOrEqualTo("score", -Double.MAX_VALUE).test(metadata))
                .isFalse();
        assertThat(new IsLessThanOrEqualTo("score", Double.MAX_VALUE).test(metadata))
                .isFalse();
    }

    @Test
    void shouldHandleFloatValuesLikeDoubleValues() {
        assertThat(new IsGreaterThan("score", 0.5f).test(metadata(Float.POSITIVE_INFINITY)))
                .isTrue();
        assertThat(new IsLessThan("score", 0.5f).test(metadata(Float.NEGATIVE_INFINITY)))
                .isTrue();
        assertThat(new IsEqualTo("score", 0.5f).test(metadata(Float.NaN))).isFalse();
        assertThat(new IsNotEqualTo("score", 0.5f).test(metadata(Float.NaN))).isTrue();
    }

    @Test
    void shouldCompareNonFiniteFloatMetadataWithDoubleValues() {
        assertThat(new IsGreaterThan("score", Double.MAX_VALUE).test(metadata(Float.POSITIVE_INFINITY)))
                .isTrue();
        assertThat(new IsLessThan("score", -Double.MAX_VALUE).test(metadata(Float.NEGATIVE_INFINITY)))
                .isTrue();
        assertThat(new IsEqualTo("score", 0.5d).test(metadata(Float.NaN))).isFalse();
    }

    @Test
    void shouldNotChangeComparisonOfFiniteValues() {
        Metadata metadata = new Metadata().put("score", 1L);

        assertThat(new IsEqualTo("score", 1).test(metadata)).isTrue();
        assertThat(new IsEqualTo("score", 1.0).test(metadata)).isTrue();
        assertThat(new IsGreaterThan("score", 0).test(metadata)).isTrue();
        assertThat(new IsIn("score", List.of(1.0, 2.0)).test(metadata)).isTrue();
        assertThat(new IsNotIn("score", List.of(2.0, 3.0)).test(metadata)).isTrue();
    }

    @Test
    void shouldKeepPrecisionBeyondDoubleForFiniteValues() {
        // 9007199254740992 and 9007199254740993 are distinct longs that collapse onto the same double,
        // so comparing them as doubles would wrongly report them as equal
        Metadata metadata = new Metadata().put("score", 9007199254740993L);

        assertThat(new IsEqualTo("score", 9007199254740992L).test(metadata)).isFalse();
        assertThat(new IsGreaterThan("score", 9007199254740992L).test(metadata)).isTrue();
    }

    private static Metadata metadata(double value) {
        return new Metadata().put("score", value);
    }

    private static Metadata metadata(float value) {
        return new Metadata().put("score", value);
    }
}
