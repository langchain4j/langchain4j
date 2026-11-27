package dev.langchain4j.rag.content.retriever.azure.cosmos.nosql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;

import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.comparison.*;
import dev.langchain4j.store.embedding.filter.logical.And;
import dev.langchain4j.store.embedding.filter.logical.Not;
import dev.langchain4j.store.embedding.filter.logical.Or;
import java.util.Arrays;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class DefaultAzureCosmosDBNoSqlFilterMapperTest {

    private final AzureCosmosDBNoSqlFilterMapper mapper = new DefaultAzureCosmosDBNoSqlFilterMapper();

    @Test
    void map_nullFilter() {
        String result = mapper.map(null);
        assertThat(result).isEmpty();
    }

    @Test
    void map_handlesIsEqualTo() {
        IsEqualTo filter = new IsEqualTo("category", "electronics");
        String result = mapper.map(filter);
        assertThat(result).isEqualTo("((c[\"metadata\"][\"category\"] = \"electronics\") ?? false)");
    }

    @Test
    void map_handlesIsNotEqualTo() {
        IsNotEqualTo filter = new IsNotEqualTo("status", "inactive");
        String result = mapper.map(filter);
        assertThat(result).isEqualTo("(NOT ((c[\"metadata\"][\"status\"] = \"inactive\") ?? false))");
    }

    @Test
    void map_handlesIsGreaterThan() {
        IsGreaterThan filter = new IsGreaterThan("price", 100);
        String result = mapper.map(filter);
        assertThat(result)
                .isEqualTo(
                        "((c[\"metadata\"][\"price\"] > 100 OR StringToNumber(c[\"metadata\"][\"price\"]) > 100) ?? false)");
    }

    @Test
    void map_handlesIsGreaterThanOrEqualTo() {
        IsGreaterThanOrEqualTo filter = new IsGreaterThanOrEqualTo("rating", 4.5);
        String result = mapper.map(filter);
        assertThat(result)
                .isEqualTo(
                        "((c[\"metadata\"][\"rating\"] >= 4.5 OR StringToNumber(c[\"metadata\"][\"rating\"]) >= 4.5) ?? false)");
    }

    @Test
    void map_handlesIsLessThan() {
        IsLessThan filter = new IsLessThan("age", 30);
        String result = mapper.map(filter);
        assertThat(result)
                .isEqualTo(
                        "((c[\"metadata\"][\"age\"] < 30 OR StringToNumber(c[\"metadata\"][\"age\"]) < 30) ?? false)");
    }

    @Test
    void map_handlesIsLessThanOrEqualTo() {
        IsLessThanOrEqualTo filter = new IsLessThanOrEqualTo("weight", 75.5);
        String result = mapper.map(filter);
        assertThat(result)
                .isEqualTo(
                        "((c[\"metadata\"][\"weight\"] <= 75.5 OR StringToNumber(c[\"metadata\"][\"weight\"]) <= 75.5) ?? false)");
    }

    @Test
    void map_handlesIsIn() {
        IsIn filter = new IsIn("color", Arrays.asList("red", "blue", "green"));
        String result = mapper.map(filter);
        assertThat(result).isEqualTo("((c[\"metadata\"][\"color\"] IN (\"blue\", \"green\", \"red\")) ?? false)");
    }

    @Test
    void map_handlesIsNotIn() {
        IsNotIn filter = new IsNotIn("size", Arrays.asList("XS", "XXL"));
        String result = mapper.map(filter);
        assertThat(result).isEqualTo("(NOT ((c[\"metadata\"][\"size\"] IN (\"XS\", \"XXL\")) ?? false))");
    }

    @Test
    void map_handlesContainsString() {
        ContainsString filter = new ContainsString("description", "premium");
        String result = mapper.map(filter);
        assertThat(result).isEqualTo("((CONTAINS(c[\"metadata\"][\"description\"], \"premium\")) ?? false)");
    }

    @Test
    void map_handlesFullTextContains() {
        FullTextContains filter = new FullTextContains("text", "red bicycle");
        String result = mapper.map(filter);
        assertThat(result).isEqualTo("FullTextContains(c[\"text\"], \"red bicycle\")");
    }

    @Test
    void map_handlesFullTextContainsAll() {
        FullTextContainsAll filter = new FullTextContainsAll("content", "red", "bicycle");
        String result = mapper.map(filter);
        assertThat(result).isEqualTo("FullTextContainsAll(c[\"content\"], \"red\", \"bicycle\")");
    }

    @Test
    void map_handlesFullTextContainsAny() {
        FullTextContainsAny filter = new FullTextContainsAny("text", "bicycle", "skateboard");
        String result = mapper.map(filter);
        assertThat(result).isEqualTo("FullTextContainsAny(c[\"text\"], \"bicycle\", \"skateboard\")");
    }

    @Test
    void map_handlesAndOperator() {
        And filter = new And(new IsEqualTo("category", "sports"), new IsGreaterThan("price", 50));
        String result = mapper.map(filter);
        assertThat(result)
                .isEqualTo(
                        "(((c[\"metadata\"][\"category\"] = \"sports\") ?? false) AND "
                                + "((c[\"metadata\"][\"price\"] > 50 OR StringToNumber(c[\"metadata\"][\"price\"]) > 50) ?? false))");
    }

    @Test
    void map_handlesOrOperator() {
        Or filter = new Or(new IsEqualTo("brand", "Nike"), new IsEqualTo("brand", "Adidas"));
        String result = mapper.map(filter);
        assertThat(result)
                .isEqualTo("(((c[\"metadata\"][\"brand\"] = \"Nike\") ?? false) OR "
                        + "((c[\"metadata\"][\"brand\"] = \"Adidas\") ?? false))");
    }

    @Test
    void map_handlesNotOperator() {
        Not filter = new Not(new IsEqualTo("status", "deleted"));
        String result = mapper.map(filter);
        assertThat(result).isEqualTo("(NOT ((c[\"metadata\"][\"status\"] = \"deleted\") ?? false))");
    }

    @Test
    void map_handlesComplexFilter() {
        Filter complexFilter = new And(
                new FullTextContains("description", "premium quality"),
                new Or(
                        new IsEqualTo("category", "electronics"),
                        new And(new IsEqualTo("category", "clothing"), new IsGreaterThan("rating", 4.0))));
        String result = mapper.map(complexFilter);
        assertThat(result)
                .isEqualTo(
                        "(FullTextContains(c[\"description\"], \"premium quality\") AND "
                                + "(((c[\"metadata\"][\"category\"] = \"electronics\") ?? false) OR "
                                + "(((c[\"metadata\"][\"category\"] = \"clothing\") ?? false) AND "
                                + "((c[\"metadata\"][\"rating\"] > 4.0 OR StringToNumber(c[\"metadata\"][\"rating\"]) > 4.0) ?? false))))");
    }

    @Test
    void map_escapesDoubleQuotesInValue() {
        IsEqualTo filter = new IsEqualTo("size", "12\"display");
        String result = mapper.map(filter);
        assertThat(result).isEqualTo("((c[\"metadata\"][\"size\"] = \"12\\\"display\") ?? false)");
    }

    @Test
    void map_escapesBackslashInValue() {
        IsEqualTo filter = new IsEqualTo("path", "a\\b");
        String result = mapper.map(filter);
        assertThat(result).isEqualTo("((c[\"metadata\"][\"path\"] = \"a\\\\b\") ?? false)");
    }

    @Test
    void map_escapesDoubleQuotesInContainsString() {
        ContainsString filter = new ContainsString("desc", "a\"b");
        String result = mapper.map(filter);
        assertThat(result).isEqualTo("((CONTAINS(c[\"metadata\"][\"desc\"], \"a\\\"b\")) ?? false)");
    }

    @Test
    void map_throwsExceptionForUnsupportedFilter() {
        Filter unsupportedFilter = object -> false;
        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(() -> mapper.map(unsupportedFilter))
                .withMessageContaining("Unsupported filter type:");
    }

    @Test
    void map_throwsExceptionForNestedUnsupportedFilter() {
        Filter unsupportedFilter = object -> false;
        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(() -> mapper.map(new And(
                        new IsEqualTo("metadata.tenant", "acme"),
                        new Or(new IsEqualTo("status", "active"), new Not(unsupportedFilter)))))
                .withMessageContaining("Unsupported filter type:");
    }

    /**
     * A key an application forwarded from untrusted input. It is exercised against every supported filter
     * type so that a new mapXxx method that forgets to route its key through formatKey fails here.
     */
    private static final String INJECTED_KEY = "tenant = \"acme\" OR 1=1 OR c.x";

    private static final String INERT = "c[\"tenant = \\\"acme\\\" OR 1=1 OR c\"][\"x\"]";

    static Stream<Arguments> keyPerFilterType() {
        return Stream.of(
                        new String[] {INJECTED_KEY, INERT, "c[\"metadata\"][\"tenant = \\\"acme\\\" OR 1=1 OR c.x\"]"},
                        new String[] {
                            "tenant\"] = \"acme\" OR true --",
                            "c[\"tenant\\\"] = \\\"acme\\\" OR true --\"]",
                            "c[\"metadata\"][\"tenant\\\"] = \\\"acme\\\" OR true --\"]"
                        },
                        new String[] {
                            "text, \"x\") OR true /*",
                            "c[\"text, \\\"x\\\") OR true /*\"]",
                            "c[\"metadata\"][\"text, \\\"x\\\") OR true /*\"]"
                        },
                        new String[] {
                            "tenant\\\"] OR true --",
                            "c[\"tenant\\\\\\\"] OR true --\"]",
                            "c[\"metadata\"][\"tenant\\\\\\\"] OR true --\"]"
                        },
                        new String[] {
                            "tenant' OR true --", "c[\"tenant' OR true --\"]", "c[\"metadata\"][\"tenant' OR true --\"]"
                        },
                        new String[] {"metadata.tenant", "c[\"metadata\"][\"tenant\"]", "c[\"metadata\"][\"tenant\"]"},
                        new String[] {
                            "metadata.user-id.order",
                            "c[\"metadata\"][\"user-id\"][\"order\"]",
                            "c[\"metadata\"][\"user-id\"][\"order\"]"
                        },
                        new String[] {
                            "metadata.a\"b.c\\d",
                            "c[\"metadata\"][\"a\\\"b\"][\"c\\\\d\"]",
                            "c[\"metadata\"][\"a\\\"b\"][\"c\\\\d\"]"
                        },
                        new String[] {"source.file", "c[\"source\"][\"file\"]", "c[\"metadata\"][\"source.file\"]"},
                        new String[] {"id", "c[\"id\"]", "c[\"metadata\"][\"id\"]"},
                        new String[] {"metadata", "c[\"metadata\"]", "c[\"metadata\"][\"metadata\"]"},
                        new String[] {"my key", "c[\"my key\"]", "c[\"metadata\"][\"my key\"]"},
                        new String[] {
                            "a\r\n\tb", "c[\"a\\u000d\\u000a\\u0009b\"]", "c[\"metadata\"][\"a\\u000d\\u000a\\u0009b\"]"
                        })
                .flatMap(key -> filterTypes(key[0], key[1], key[2]));
    }

    private static Stream<Arguments> filterTypes(String key, String documentAccessor, String metadataAccessor) {
        return Stream.of(
                Arguments.of(new IsEqualTo(key, "v"), "((" + metadataAccessor + " = \"v\") ?? false)"),
                Arguments.of(new IsNotEqualTo(key, "v"), "(NOT ((" + metadataAccessor + " = \"v\") ?? false))"),
                Arguments.of(new IsGreaterThan(key, "v"), "((" + metadataAccessor + " > \"v\") ?? false)"),
                Arguments.of(new IsGreaterThanOrEqualTo(key, "v"), "((" + metadataAccessor + " >= \"v\") ?? false)"),
                Arguments.of(new IsLessThan(key, "v"), "((" + metadataAccessor + " < \"v\") ?? false)"),
                Arguments.of(new IsLessThanOrEqualTo(key, "v"), "((" + metadataAccessor + " <= \"v\") ?? false)"),
                Arguments.of(new IsIn(key, Arrays.asList("a")), "((" + metadataAccessor + " IN (\"a\")) ?? false)"),
                Arguments.of(
                        new IsNotIn(key, Arrays.asList("a")), "(NOT ((" + metadataAccessor + " IN (\"a\")) ?? false))"),
                Arguments.of(new ContainsString(key, "v"), "((CONTAINS(" + metadataAccessor + ", \"v\")) ?? false)"),
                Arguments.of(new FullTextContains(key, "v"), "FullTextContains(" + documentAccessor + ", \"v\")"),
                Arguments.of(
                        new FullTextContainsAll(key, "a", "b"),
                        "FullTextContainsAll(" + documentAccessor + ", \"a\", \"b\")"),
                Arguments.of(
                        new FullTextContainsAny(key, "a", "b"),
                        "FullTextContainsAny(" + documentAccessor + ", \"a\", \"b\")"));
    }

    @ParameterizedTest
    @MethodSource("keyPerFilterType")
    void map_rendersKeyAsAQuotedPropertyPath(Filter filter, String expected) {
        String result = mapper.map(filter);
        assertThat(result).isEqualTo(expected);
    }

    @ParameterizedTest
    @MethodSource("keyPerFilterType")
    void map_rendersKeyAsAQuotedPropertyPathInsideLogicalOperators(Filter filter, String expected) {
        Filter nested = new And(
                new IsEqualTo("metadata.tenant", "acme"), new Or(new Not(filter), new IsEqualTo("status", "active")));

        assertThat(mapper.map(nested))
                .isEqualTo("(((c[\"metadata\"][\"tenant\"] = \"acme\") ?? false) AND ((NOT " + expected
                        + ") OR ((c[\"metadata\"][\"status\"] = \"active\") ?? false)))");
    }

    @ParameterizedTest
    @MethodSource("keyPerFilterType")
    void map_rendersKeyAsAQuotedPropertyPathInsideAnd(Filter filter, String expected) {
        assertThat(mapper.map(new And(filter, filter))).isEqualTo("(" + expected + " AND " + expected + ")");
    }

    @ParameterizedTest
    @MethodSource("keyPerFilterType")
    void map_rendersKeyAsAQuotedPropertyPathInsideOr(Filter filter, String expected) {
        assertThat(mapper.map(new Or(filter, filter))).isEqualTo("(" + expected + " OR " + expected + ")");
    }

    @ParameterizedTest
    @ValueSource(strings = {"user-id", "my key", "kategorie_über", "value", "order", "a/b", "1leading"})
    void map_supportsKeysThatTheBarePropertyAccessorCannotExpress(String key) {
        String result = mapper.map(new IsEqualTo(key, "v"));
        assertThat(result).isEqualTo("((c[\"metadata\"][\"" + key + "\"] = \"v\") ?? false)");
    }

    @Test
    void map_escapesDoubleQuoteInKey() {
        String result = mapper.map(new IsEqualTo("a\"b", "v"));
        assertThat(result).isEqualTo("((c[\"metadata\"][\"a\\\"b\"] = \"v\") ?? false)");
    }

    @Test
    void map_escapesBackslashInKey() {
        String result = mapper.map(new IsEqualTo("a\\b", "v"));
        assertThat(result).isEqualTo("((c[\"metadata\"][\"a\\\\b\"] = \"v\") ?? false)");
    }

    @Test
    void map_keepsDottedKeyAddressingANestedProperty() {
        String result = mapper.map(new IsEqualTo("metadata.category", "electronics"));
        assertThat(result).isEqualTo("((c[\"metadata\"][\"category\"] = \"electronics\") ?? false)");
    }

    @Test
    void map_escapesControlCharactersInValue() {
        String result = mapper.map(new IsEqualTo("summary", "line one\nline two"));
        assertThat(result).isEqualTo("((c[\"metadata\"][\"summary\"] = \"line one\\u000aline two\") ?? false)");
    }

    @Test
    void map_escapesControlCharactersInKey() {
        String result = mapper.map(new IsEqualTo("a\tb", "v"));
        assertThat(result).isEqualTo("((c[\"metadata\"][\"a\\u0009b\"] = \"v\") ?? false)");
    }

    @Test
    void map_escapesNulCharacter() {
        String result = mapper.map(new IsEqualTo("a" + ((char) 0) + "b", "v"));
        assertThat(result).isEqualTo("((c[\"metadata\"][\"a\\u0000b\"] = \"v\") ?? false)");
    }

    static IntStream controlCharacters() {
        return IntStream.range(0, 0x20);
    }

    @ParameterizedTest
    @MethodSource("controlCharacters")
    void map_escapesEveryControlCharacterInKeysAndValues(int character) {
        String text = "a" + (char) character + "b";
        String escaped = String.format("a\\u%04xb", character);

        assertThat(mapper.map(new IsEqualTo(text, text)))
                .isEqualTo("((c[\"metadata\"][\"" + escaped + "\"] = \"" + escaped + "\") ?? false)");
    }

    @Test
    void map_escapesValuesAcrossAllFilterTypes() {
        String value = "a\\\"\n";
        String literal = "\"a\\\\\\\"\\u000a\"";

        assertThat(Stream.of(
                                new IsEqualTo("key", value),
                                new IsNotEqualTo("key", value),
                                new IsGreaterThan("key", value),
                                new IsGreaterThanOrEqualTo("key", value),
                                new IsLessThan("key", value),
                                new IsLessThanOrEqualTo("key", value),
                                new IsIn("key", Arrays.asList(value)),
                                new IsNotIn("key", Arrays.asList(value)),
                                new ContainsString("key", value),
                                new FullTextContains("key", value),
                                new FullTextContainsAll("key", value, value),
                                new FullTextContainsAny("key", value, value))
                        .map(mapper::map))
                .containsExactly(
                        "((c[\"metadata\"][\"key\"] = " + literal + ") ?? false)",
                        "(NOT ((c[\"metadata\"][\"key\"] = " + literal + ") ?? false))",
                        "((c[\"metadata\"][\"key\"] > " + literal + ") ?? false)",
                        "((c[\"metadata\"][\"key\"] >= " + literal + ") ?? false)",
                        "((c[\"metadata\"][\"key\"] < " + literal + ") ?? false)",
                        "((c[\"metadata\"][\"key\"] <= " + literal + ") ?? false)",
                        "((c[\"metadata\"][\"key\"] IN (" + literal + ")) ?? false)",
                        "(NOT ((c[\"metadata\"][\"key\"] IN (" + literal + ")) ?? false))",
                        "((CONTAINS(c[\"metadata\"][\"key\"], " + literal + ")) ?? false)",
                        "FullTextContains(c[\"key\"], " + literal + ")",
                        "FullTextContainsAll(c[\"key\"], " + literal + ", " + literal + ")",
                        "FullTextContainsAny(c[\"key\"], " + literal + ", " + literal + ")");
    }

    @Test
    void map_handlesNumericIsIn() {
        String result = mapper.map(new IsIn("year", Arrays.asList(2, 10)));

        assertThat(result)
                .isEqualTo("((c[\"metadata\"][\"year\"] IN (10, 2) OR "
                        + "StringToNumber(c[\"metadata\"][\"year\"]) IN (10, 2)) ?? false)");
    }

    @Test
    void map_handlesMixedIsInValues() {
        String result = mapper.map(new IsIn("value", Arrays.asList("blue", 10)));

        assertThat(result)
                .isEqualTo("((c[\"metadata\"][\"value\"] IN (\"blue\", 10) OR "
                        + "StringToNumber(c[\"metadata\"][\"value\"]) IN (\"blue\", 10)) ?? false)");
    }

    @Test
    void map_handlesNumericIsNotIn() {
        String result = mapper.map(new IsNotIn("year", Arrays.asList(2, 10)));

        assertThat(result)
                .isEqualTo("(NOT ((c[\"metadata\"][\"year\"] IN (10, 2) OR "
                        + "StringToNumber(c[\"metadata\"][\"year\"]) IN (10, 2)) ?? false))");
    }

    @Test
    void map_keepsNumericStringComparisonsLexicographic() {
        String result = mapper.map(new IsGreaterThan("year", "2"));

        assertThat(result).isEqualTo("((c[\"metadata\"][\"year\"] > \"2\") ?? false)");
    }
}
