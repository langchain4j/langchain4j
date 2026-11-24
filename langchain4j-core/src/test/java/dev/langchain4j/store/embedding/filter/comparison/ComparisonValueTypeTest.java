package dev.langchain4j.store.embedding.filter.comparison;

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import dev.langchain4j.data.document.Metadata;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.LocalTime;
import java.time.MonthDay;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.Year;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class ComparisonValueTypeTest {

    enum Color {
        RED,
        GREEN {
            @Override
            public String toString() {
                return "green";
            }
        }
    }

    static Stream<Comparable<?>> supportedValues() {
        return Stream.of(
                "text",
                UUID.randomUUID(),
                true,
                1,
                1L,
                1.5f,
                1.5d,
                (byte) 1,
                (short) 1,
                new BigInteger("1"),
                new BigDecimal("1.5"),
                Color.RED,
                // an enum constant with a body is an anonymous subclass of the enum
                Color.GREEN,
                LocalDate.of(2026, 1, 1),
                LocalDateTime.of(2026, 1, 1, 10, 0),
                LocalTime.of(10, 0),
                OffsetDateTime.of(2026, 1, 1, 10, 0, 0, 0, ZoneOffset.UTC),
                ZonedDateTime.of(2026, 1, 1, 10, 0, 0, 0, ZoneOffset.UTC),
                Instant.parse("2026-01-01T10:00:00Z"),
                OffsetTime.of(10, 0, 0, 0, ZoneOffset.UTC),
                Year.of(2026),
                YearMonth.of(2026, 1),
                MonthDay.of(1, 1),
                new Date(0),
                new java.sql.Date(0),
                new Time(0),
                new Timestamp(0));
    }

    static Stream<Object> unsupportedValues() {
        return Stream.concat(
                unsupportedComparableValues(),
                Stream.of(
                        List.of("a"),
                        Map.of("a", "b"),
                        new int[] {1},
                        new AtomicInteger(1),
                        new Object()));
    }

    static Stream<Comparable<?>> unsupportedComparableValues() {
        return Stream.of(
                new StringBuilder("a"),
                'x',
                // not final, so a subclass could change toString(); only the exact classes are accepted
                new Date(0) {
                    @Override
                    public String toString() {
                        return "2";
                    }
                },
                // not final, so a subclass could change toString(); only the exact classes are accepted
                new BigDecimal("1") {
                    @Override
                    public String toString() {
                        return "2";
                    }
                },
                new BigInteger("1") {
                    @Override
                    public String toString() {
                        return "2";
                    }
                });
    }

    @ParameterizedTest
    @MethodSource("supportedValues")
    void shouldAcceptSupportedValueTypes(Comparable<?> value) {
        assertThat(new IsEqualTo("key", value).comparisonValue()).isEqualTo(value);
        assertThat(new IsNotEqualTo("key", value).comparisonValue()).isEqualTo(value);
        assertThat(new IsGreaterThan("key", value).comparisonValue()).isEqualTo(value);
        assertThat(new IsGreaterThanOrEqualTo("key", value).comparisonValue()).isEqualTo(value);
        assertThat(new IsLessThan("key", value).comparisonValue()).isEqualTo(value);
        assertThat(new IsLessThanOrEqualTo("key", value).comparisonValue()).isEqualTo(value);
        assertThat(new IsIn("key", List.of(value)).comparisonValues()).isEqualTo(Set.of(value));
        assertThat(new IsNotIn("key", List.of(value)).comparisonValues()).isEqualTo(Set.of(value));
    }

    @Test
    void shouldAcceptMixedSupportedValueTypesInCollections() {
        List<Object> values = List.of("text", 1, 2L, 3.5f, 4.5d, true, UUID.randomUUID());

        assertThat(new IsIn("key", values).comparisonValues()).isEqualTo(Set.copyOf(values));
        assertThat(new IsNotIn("key", values).comparisonValues()).isEqualTo(Set.copyOf(values));
    }

    @ParameterizedTest
    @MethodSource("unsupportedValues")
    void shouldRejectUnsupportedValueTypesInEqualityAndCollectionFilters(Object value) {
        String expectedMessage = expectedMessage(value);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> new IsEqualTo("key", value))
                .withMessage(expectedMessage);
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new IsNotEqualTo("key", value))
                .withMessage(expectedMessage);
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new IsIn("key", List.of(value)))
                .withMessage(expectedCollectionMessage(value));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new IsNotIn("key", List.of(value)))
                .withMessage(expectedCollectionMessage(value));
    }

    @ParameterizedTest
    @MethodSource("unsupportedComparableValues")
    void shouldRejectUnsupportedValueTypesInRangeFilters(Comparable<?> value) {
        String expectedMessage = expectedMessage(value);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> new IsGreaterThan("key", value))
                .withMessage(expectedMessage);
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new IsGreaterThanOrEqualTo("key", value))
                .withMessage(expectedMessage);
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new IsLessThan("key", value))
                .withMessage(expectedMessage);
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new IsLessThanOrEqualTo("key", value))
                .withMessage(expectedMessage);
    }

    @Test
    void shouldRejectNestedCollectionMixedWithSupportedValues() {
        // what Jackson produces for {"tags": ["a", 1, ["b"]]} when parsed into a List<Object>
        List<Object> values = List.of("a", 1, List.of("b"));
        String expectedMessage = expectedCollectionMessage(values.get(2));

        assertThatIllegalArgumentException()
                .isThrownBy(() -> metadataKey("key").isIn(values))
                .withMessage(expectedMessage);
        assertThatIllegalArgumentException()
                .isThrownBy(() -> metadataKey("key").isNotIn(values))
                .withMessage(expectedMessage);
    }

    static Stream<Comparable<?>> nonFiniteValues() {
        return Stream.of(
                Float.NaN,
                Float.POSITIVE_INFINITY,
                Float.NEGATIVE_INFINITY,
                Double.NaN,
                Double.POSITIVE_INFINITY,
                Double.NEGATIVE_INFINITY);
    }

    @ParameterizedTest
    @MethodSource("nonFiniteValues")
    void shouldRejectNonFiniteNumbers(Comparable<?> value) {
        String expectedMessage = "comparisonValue with key 'key' must be a finite number";
        String expectedCollectionMessage = "comparisonValues with key 'key' must contain only finite numbers";

        assertThatIllegalArgumentException()
                .isThrownBy(() -> new IsEqualTo("key", value))
                .withMessage(expectedMessage);
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new IsNotEqualTo("key", value))
                .withMessage(expectedMessage);
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new IsGreaterThan("key", value))
                .withMessage(expectedMessage);
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new IsGreaterThanOrEqualTo("key", value))
                .withMessage(expectedMessage);
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new IsLessThan("key", value))
                .withMessage(expectedMessage);
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new IsLessThanOrEqualTo("key", value))
                .withMessage(expectedMessage);
        assertThatIllegalArgumentException()
                .isThrownBy(() -> metadataKey("key").isIn(List.of(1.5d, value)))
                .withMessage(expectedCollectionMessage);
        assertThatIllegalArgumentException()
                .isThrownBy(() -> metadataKey("key").isNotIn(List.of(1.5d, value)))
                .withMessage(expectedCollectionMessage);
    }

    @Test
    void shouldAcceptLargestFiniteNumbers() {
        assertThat(new IsLessThan("key", Double.MAX_VALUE).comparisonValue()).isEqualTo(Double.MAX_VALUE);
        assertThat(new IsGreaterThan("key", -Float.MAX_VALUE).comparisonValue()).isEqualTo(-Float.MAX_VALUE);
        assertThat(metadataKey("key").isIn(Double.MIN_VALUE, -0.0d)).isInstanceOf(IsIn.class);
    }

    @Test
    void shouldCompareWidenedNumberTypesWithStoredNumbers() {
        Metadata metadata = Metadata.from(Map.of("price", 1.5d, "count", 7));

        assertThat(new IsEqualTo("price", new BigDecimal("1.5")).test(metadata)).isTrue();
        assertThat(new IsLessThan("price", new BigDecimal("1.4")).test(metadata)).isFalse();
        assertThat(new IsGreaterThan("count", new BigInteger("6")).test(metadata)).isTrue();
        assertThat(new IsEqualTo("count", (byte) 7).test(metadata)).isTrue();
        assertThat(new IsLessThanOrEqualTo("count", (short) 6).test(metadata)).isFalse();
        assertThat(new IsIn("count", List.of((short) 7, new BigDecimal("8"))).test(metadata)).isTrue();
        assertThat(new IsNotIn("count", List.of((byte) 7)).test(metadata)).isFalse();
    }

    private static String expectedMessage(Object value) {
        return "Unsupported type of comparisonValue with key 'key': " + value.getClass().getName() + ". "
                + SUPPORTED_TYPES_HINT;
    }

    private static String expectedCollectionMessage(Object value) {
        return "Unsupported type of a value in comparisonValues with key 'key': " + value.getClass().getName() + ". "
                + SUPPORTED_TYPES_HINT;
    }

    private static final String SUPPORTED_TYPES_HINT =
            "Supported types are String, UUID, Boolean, Integer, Long, Float, Double, Byte, Short, BigInteger, "
                    + "BigDecimal, LocalDate, LocalDateTime, LocalTime, OffsetDateTime, OffsetTime, ZonedDateTime, Instant, "
                    + "Year, YearMonth, MonthDay, java.util.Date, java.sql.Date, java.sql.Time, java.sql.Timestamp and enums. "
                    + "Convert the value to one of these types first, for example a Character with String.valueOf().";
}
