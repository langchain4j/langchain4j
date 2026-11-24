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
import java.util.Collection;
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
    private static final String PLACEHOLDER = "placeholder";
    private final AzureCosmosDBNoSqlFilterMapper mapper = new DefaultAzureCosmosDBNoSqlFilterMapper();

    @Test
    void rejects_list_value_instead_of_bypassing_trusted_tenant_filter() {
        Filter filter = new And(
                new IsEqualTo("metadata.tenant", "acme"), unvalidatedIsEqualTo("metadata.category", List.of(PAYLOAD)));

        assertThatIllegalArgumentException()
                .isThrownBy(() -> mapper.map(filter))
                .withMessageContaining("Unsupported comparison value type:");
    }

    @Test
    void rejects_list_value_when_the_filter_is_created() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new IsEqualTo("metadata.category", List.of(PAYLOAD)))
                .withMessageStartingWith("Unsupported type of comparisonValue with key 'metadata.category': ");
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
                    .withMessageStartingWith(expectedMessagePrefix(filter, value));
            Filter nested = new And(
                    new IsEqualTo("metadata.tenant", "acme"),
                    new Or(new IsEqualTo("status", "active"), new Not(filter)));
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> mapper.map(nested))
                    .withMessageStartingWith(expectedMessagePrefix(filter, value));
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
                        .withMessage(expectedNonFiniteMessage(filter)));
    }

    @ParameterizedTest
    @MethodSource("nonFiniteValues")
    void filter_classes_reject_non_finite_numbers_before_mapping(Object value) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new IsEqualTo("key", value))
                .withMessage("comparisonValue with key 'key' must be a finite number");
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
        IsIn isIn = unvalidatedIsIn("key", values);
        IsNotIn isNotIn = unvalidatedIsNotIn("key", values);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> mapper.map(isIn))
                .withMessageStartingWith(expectedMessagePrefix(isIn, values.get(2)));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> mapper.map(isNotIn))
                .withMessageStartingWith(expectedMessagePrefix(isNotIn, values.get(2)));
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

    // The filter classes reject unsupported values themselves, so the values are returned from overridden
    // accessors to check that the mapper rejects them on its own.
    private static Stream<Filter> comparisonFilters(Object value) {
        Stream<Filter> filters = Stream.of(
                unvalidatedIsEqualTo("key", value),
                new IsNotEqualTo("key", PLACEHOLDER) {
                    @Override
                    public Object comparisonValue() {
                        return value;
                    }
                },
                unvalidatedIsIn("key", List.of(value)),
                unvalidatedIsNotIn("key", List.of(value)));
        if (value instanceof Comparable<?> comparable) {
            return Stream.concat(
                    filters,
                    Stream.of(
                            new IsGreaterThan("key", PLACEHOLDER) {
                                @Override
                                public Comparable<?> comparisonValue() {
                                    return comparable;
                                }
                            },
                            new IsGreaterThanOrEqualTo("key", PLACEHOLDER) {
                                @Override
                                public Comparable<?> comparisonValue() {
                                    return comparable;
                                }
                            },
                            new IsLessThan("key", PLACEHOLDER) {
                                @Override
                                public Comparable<?> comparisonValue() {
                                    return comparable;
                                }
                            },
                            new IsLessThanOrEqualTo("key", PLACEHOLDER) {
                                @Override
                                public Comparable<?> comparisonValue() {
                                    return comparable;
                                }
                            }));
        }
        return filters;
    }

    private static String expectedMessagePrefix(Filter filter, Object value) {
        // The mapper maps IsNotEqualTo and IsNotIn by negating a new IsEqualTo or IsIn,
        // so for these two the filter classes reject the value before the mapper's own check
        if (filter instanceof IsNotEqualTo) {
            return "Unsupported type of comparisonValue with key 'key': " + value.getClass().getName();
        }
        if (filter instanceof IsNotIn) {
            return "Unsupported type of a value in comparisonValues with key 'key': " + value.getClass().getName();
        }
        return "Unsupported comparison value type: " + value.getClass().getName();
    }

    private static String expectedNonFiniteMessage(Filter filter) {
        // The mapper maps IsNotEqualTo and IsNotIn by negating a new IsEqualTo or IsIn,
        // so for these two the filter classes reject the value before the mapper's own check
        if (filter instanceof IsNotEqualTo) {
            return "comparisonValue with key 'key' must be a finite number";
        }
        if (filter instanceof IsNotIn) {
            return "comparisonValues with key 'key' must contain only finite numbers";
        }
        return "Comparison value must be a finite number";
    }

    private static IsEqualTo unvalidatedIsEqualTo(String key, Object value) {
        return new IsEqualTo(key, PLACEHOLDER) {
            @Override
            public Object comparisonValue() {
                return value;
            }
        };
    }

    private static IsIn unvalidatedIsIn(String key, Collection<?> values) {
        return new IsIn(key, List.of(PLACEHOLDER)) {
            @Override
            public Collection<?> comparisonValues() {
                return values;
            }
        };
    }

    private static IsNotIn unvalidatedIsNotIn(String key, Collection<?> values) {
        return new IsNotIn(key, List.of(PLACEHOLDER)) {
            @Override
            public Collection<?> comparisonValues() {
                return values;
            }
        };
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
