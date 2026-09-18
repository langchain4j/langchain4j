package dev.langchain4j.rag.content.retriever.azure.cosmos.nosql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.comparison.IsEqualTo;
import dev.langchain4j.store.embedding.filter.comparison.IsGreaterThan;
import dev.langchain4j.store.embedding.filter.comparison.IsGreaterThanOrEqualTo;
import dev.langchain4j.store.embedding.filter.comparison.IsIn;
import dev.langchain4j.store.embedding.filter.comparison.IsLessThan;
import dev.langchain4j.store.embedding.filter.comparison.IsLessThanOrEqualTo;
import dev.langchain4j.store.embedding.filter.comparison.IsNotEqualTo;
import dev.langchain4j.store.embedding.filter.comparison.IsNotIn;
import dev.langchain4j.store.embedding.filter.logical.And;
import dev.langchain4j.store.embedding.filter.logical.Not;
import dev.langchain4j.store.embedding.filter.logical.Or;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class DefaultAzureCosmosDBNoSqlFilterMapperValueTest {

    private static final String PAYLOAD = "0] OR true OR c.id = [0";
    private final AzureCosmosDBNoSqlFilterMapper mapper = new DefaultAzureCosmosDBNoSqlFilterMapper();

    @Test
    void rejects_list_value_instead_of_bypassing_trusted_tenant_filter() {
        Filter filter =
                new And(new IsEqualTo("metadata.tenant", "acme"), new IsEqualTo("metadata.category", List.of(PAYLOAD)));

        assertThatIllegalArgumentException()
                .isThrownBy(() -> mapper.map(filter))
                .withMessageContaining("Unsupported comparison value type:");
    }

    static Stream<Arguments> unsupportedValues() {
        return Stream.of(
                        List.of(PAYLOAD),
                        Map.of("key", PAYLOAD),
                        new String[] {PAYLOAD},
                        new int[] {0},
                        new StringBuilder(PAYLOAD),
                        Character.valueOf('x'),
                        new Object(),
                        new AtomicInteger(0),
                        new InjectedNumber(),
                        new BigInteger("0") {
                            @Override
                            public String toString() {
                                return PAYLOAD;
                            }
                        },
                        new BigDecimal("0") {
                            @Override
                            public String toString() {
                                return PAYLOAD;
                            }
                        })
                .map(value -> Arguments.of(value));
    }

    @ParameterizedTest
    @MethodSource("unsupportedValues")
    void rejects_unsupported_values_in_every_applicable_comparison(Object value) {
        comparisonFilters(value).forEach(filter -> {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> mapper.map(filter))
                    .withMessage("Unsupported comparison value type: "
                            + value.getClass().getName());
            Filter nested = new And(
                    new IsEqualTo("metadata.tenant", "acme"),
                    new Or(new IsEqualTo("status", "active"), new Not(filter)));
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> mapper.map(nested))
                    .withMessage("Unsupported comparison value type: "
                            + value.getClass().getName());
        });
    }

    static Stream<Arguments> nonFiniteValues() {
        return Stream.of(
                        Float.NaN,
                        Float.POSITIVE_INFINITY,
                        Float.NEGATIVE_INFINITY,
                        Double.NaN,
                        Double.POSITIVE_INFINITY,
                        Double.NEGATIVE_INFINITY)
                .map(value -> Arguments.of(value));
    }

    @ParameterizedTest
    @MethodSource("nonFiniteValues")
    void rejects_non_finite_numbers_in_every_comparison(Object value) {
        comparisonFilters(value)
                .forEach(filter -> assertThatIllegalArgumentException()
                        .isThrownBy(() -> mapper.map(filter))
                        .withMessage("Comparison value must be a finite number"));
    }

    static Stream<Arguments> supportedValues() {
        return Stream.of(
                Arguments.of("a\\\"\n", "\"a\\\\\\\"\\u000a\""),
                Arguments.of(PAYLOAD, "\"" + PAYLOAD + "\""),
                Arguments.of(
                        UUID.fromString("1d6f6d7b-4e4a-4dc9-8c7e-8d0ec408e34a"),
                        "\"1d6f6d7b-4e4a-4dc9-8c7e-8d0ec408e34a\""),
                Arguments.of(true, "true"),
                Arguments.of(false, "false"),
                Arguments.of((byte) -128, "-128"),
                Arguments.of((short) 32767, "32767"),
                Arguments.of(Integer.MIN_VALUE, "-2147483648"),
                Arguments.of(Long.MAX_VALUE, "9223372036854775807"),
                Arguments.of(-0.0f, "-0.0"),
                Arguments.of(Float.MIN_VALUE, "1.4E-45"),
                Arguments.of(Float.MAX_VALUE, "3.4028235E38"),
                Arguments.of(-0.0d, "-0.0"),
                Arguments.of(Double.MIN_VALUE, "4.9E-324"),
                Arguments.of(Double.MAX_VALUE, "1.7976931348623157E308"),
                Arguments.of(new BigInteger("12345678901234567890"), "12345678901234567890"),
                Arguments.of(new BigDecimal("123.4500"), "123.4500"),
                Arguments.of(new BigDecimal("1E+30"), "1E+30"));
    }

    @ParameterizedTest
    @MethodSource("supportedValues")
    void preserves_safe_scalar_literals_in_every_comparison(Object value, String literal) {
        assertThat(comparisonFilters(value).map(mapper::map))
                .containsExactly(
                        "c[\"key\"] = " + literal,
                        "(NOT c[\"key\"] = " + literal + ")",
                        "c[\"key\"] IN (" + literal + ")",
                        "(NOT c[\"key\"] IN (" + literal + "))",
                        "c[\"key\"] > " + literal,
                        "c[\"key\"] >= " + literal,
                        "c[\"key\"] < " + literal,
                        "c[\"key\"] <= " + literal);
    }

    @Test
    void rejects_unsupported_elements_mixed_with_safe_in_values() {
        List<Object> values = List.of("safe", 1, List.of(PAYLOAD));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> mapper.map(new IsIn("key", values)))
                .withMessageContaining("Unsupported comparison value type:");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> mapper.map(new IsNotIn("key", values)))
                .withMessageContaining("Unsupported comparison value type:");
    }

    @Test
    void rejects_null_full_text_terms_with_a_clear_error() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> mapper.map(new FullTextContainsAll("text", Arrays.asList("safe", null))))
                .withMessage("comparisonValue cannot be null");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> mapper.map(new FullTextContainsAny("text", Arrays.asList("safe", null))))
                .withMessage("comparisonValue cannot be null");
    }

    private static Stream<Filter> comparisonFilters(Object value) {
        Stream<Filter> filters = Stream.of(
                new IsEqualTo("key", value),
                new IsNotEqualTo("key", value),
                new IsIn("key", List.of(value)),
                new IsNotIn("key", List.of(value)));
        if (value instanceof Comparable<?> comparable) {
            return Stream.concat(
                    filters,
                    Stream.of(
                            new IsGreaterThan("key", comparable),
                            new IsGreaterThanOrEqualTo("key", comparable),
                            new IsLessThan("key", comparable),
                            new IsLessThanOrEqualTo("key", comparable)));
        }
        return filters;
    }

    private static class InjectedNumber extends Number implements Comparable<InjectedNumber> {
        @Override
        public int intValue() {
            throw new AssertionError("Unsupported numbers must not be coerced");
        }

        @Override
        public long longValue() {
            throw new AssertionError("Unsupported numbers must not be coerced");
        }

        @Override
        public float floatValue() {
            throw new AssertionError("Unsupported numbers must not be coerced");
        }

        @Override
        public double doubleValue() {
            throw new AssertionError("Unsupported numbers must not be coerced");
        }

        @Override
        public int compareTo(InjectedNumber other) {
            throw new AssertionError("Unsupported numbers must not be compared");
        }

        @Override
        public String toString() {
            return PAYLOAD;
        }
    }
}
