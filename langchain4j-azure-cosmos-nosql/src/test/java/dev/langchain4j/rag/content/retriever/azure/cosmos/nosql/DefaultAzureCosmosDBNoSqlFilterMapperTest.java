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
        assertThat(result).isEqualTo("c[\"category\"] = \"electronics\"");
    }

    @Test
    void map_handlesIsNotEqualTo() {
        IsNotEqualTo filter = new IsNotEqualTo("status", "inactive");
        String result = mapper.map(filter);
        assertThat(result).isEqualTo("(NOT c[\"status\"] = \"inactive\")");
    }

    @Test
    void map_handlesIsGreaterThan() {
        IsGreaterThan filter = new IsGreaterThan("price", 100);
        String result = mapper.map(filter);
        assertThat(result).isEqualTo("c[\"price\"] > 100");
    }

    @Test
    void map_handlesIsGreaterThanOrEqualTo() {
        IsGreaterThanOrEqualTo filter = new IsGreaterThanOrEqualTo("rating", 4.5);
        String result = mapper.map(filter);
        assertThat(result).isEqualTo("c[\"rating\"] >= 4.5");
    }

    @Test
    void map_handlesIsLessThan() {
        IsLessThan filter = new IsLessThan("age", 30);
        String result = mapper.map(filter);
        assertThat(result).isEqualTo("c[\"age\"] < 30");
    }

    @Test
    void map_handlesIsLessThanOrEqualTo() {
        IsLessThanOrEqualTo filter = new IsLessThanOrEqualTo("weight", 75.5);
        String result = mapper.map(filter);
        assertThat(result).isEqualTo("c[\"weight\"] <= 75.5");
    }

    @Test
    void map_handlesIsIn() {
        IsIn filter = new IsIn("color", Arrays.asList("red", "blue", "green"));
        String result = mapper.map(filter);
        assertThat(result).isEqualTo("c[\"color\"] IN (\"blue\", \"green\", \"red\")");
    }

    @Test
    void map_handlesIsNotIn() {
        IsNotIn filter = new IsNotIn("size", Arrays.asList("XS", "XXL"));
        String result = mapper.map(filter);
        assertThat(result).isEqualTo("(NOT c[\"size\"] IN (\"XS\", \"XXL\"))");
    }

    @Test
    void map_handlesContainsString() {
        ContainsString filter = new ContainsString("description", "premium");
        String result = mapper.map(filter);
        assertThat(result).isEqualTo("CONTAINS(c[\"description\"], \"premium\")");
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
        assertThat(result).isEqualTo("(c[\"category\"] = \"sports\" AND c[\"price\"] > 50)");
    }

    @Test
    void map_handlesOrOperator() {
        Or filter = new Or(new IsEqualTo("brand", "Nike"), new IsEqualTo("brand", "Adidas"));
        String result = mapper.map(filter);
        assertThat(result).isEqualTo("(c[\"brand\"] = \"Nike\" OR c[\"brand\"] = \"Adidas\")");
    }

    @Test
    void map_handlesNotOperator() {
        Not filter = new Not(new IsEqualTo("status", "deleted"));
        String result = mapper.map(filter);
        assertThat(result).isEqualTo("(NOT c[\"status\"] = \"deleted\")");
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
                        "(FullTextContains(c[\"description\"], \"premium quality\") AND (c[\"category\"] = \"electronics\" OR (c[\"category\"] = \"clothing\" AND c[\"rating\"] > 4.0)))");
    }

    @Test
    void map_escapesDoubleQuotesInValue() {
        IsEqualTo filter = new IsEqualTo("size", "12\"display");
        String result = mapper.map(filter);
        assertThat(result).isEqualTo("c[\"size\"] = \"12\\\"display\"");
    }

    @Test
    void map_escapesBackslashInValue() {
        IsEqualTo filter = new IsEqualTo("path", "a\\b");
        String result = mapper.map(filter);
        assertThat(result).isEqualTo("c[\"path\"] = \"a\\\\b\"");
    }

    @Test
    void map_escapesDoubleQuotesInContainsString() {
        ContainsString filter = new ContainsString("desc", "a\"b");
        String result = mapper.map(filter);
        assertThat(result).isEqualTo("CONTAINS(c[\"desc\"], \"a\\\"b\")");
    }

    @Test
    void map_throwsExceptionForUnsupportedFilter() {
        Filter unsupportedFilter = new Filter() {
            @Override
            public boolean test(Object object) {
                return false;
            }
        };
        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(() -> mapper.map(unsupportedFilter))
                .withMessageContaining("Unsupported filter type:");
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
                        new String[] {INJECTED_KEY, INERT},
                        new String[] {"tenant\"] = \"acme\" OR true --", "c[\"tenant\\\"] = \\\"acme\\\" OR true --\"]"
                        },
                        new String[] {"text, \"x\") OR true /*", "c[\"text, \\\"x\\\") OR true /*\"]"},
                        new String[] {"tenant\\\"] OR true --", "c[\"tenant\\\\\\\"] OR true --\"]"},
                        new String[] {"tenant' OR true --", "c[\"tenant' OR true --\"]"},
                        new String[] {"metadata.tenant", "c[\"metadata\"][\"tenant\"]"},
                        new String[] {"metadata.user-id.order", "c[\"metadata\"][\"user-id\"][\"order\"]"},
                        new String[] {"metadata.a\"b.c\\d", "c[\"metadata\"][\"a\\\"b\"][\"c\\\\d\"]"},
                        new String[] {"my key", "c[\"my key\"]"},
                        new String[] {"a\r\n\tb", "c[\"a\\u000d\\u000a\\u0009b\"]"})
                .flatMap(key -> filterTypes(key[0], key[1]));
    }

    private static Stream<Arguments> filterTypes(String key, String accessor) {
        return Stream.of(
                Arguments.of(new IsEqualTo(key, "v"), accessor + " = \"v\""),
                Arguments.of(new IsNotEqualTo(key, "v"), "(NOT " + accessor + " = \"v\")"),
                Arguments.of(new IsGreaterThan(key, 1), accessor + " > 1"),
                Arguments.of(new IsGreaterThanOrEqualTo(key, 1), accessor + " >= 1"),
                Arguments.of(new IsLessThan(key, 1), accessor + " < 1"),
                Arguments.of(new IsLessThanOrEqualTo(key, 1), accessor + " <= 1"),
                Arguments.of(new IsIn(key, Arrays.asList("a")), accessor + " IN (\"a\")"),
                Arguments.of(new IsNotIn(key, Arrays.asList("a")), "(NOT " + accessor + " IN (\"a\"))"),
                Arguments.of(new ContainsString(key, "v"), "CONTAINS(" + accessor + ", \"v\")"),
                Arguments.of(new FullTextContains(key, "v"), "FullTextContains(" + accessor + ", \"v\")"),
                Arguments.of(
                        new FullTextContainsAll(key, "a", "b"), "FullTextContainsAll(" + accessor + ", \"a\", \"b\")"),
                Arguments.of(
                        new FullTextContainsAny(key, "a", "b"), "FullTextContainsAny(" + accessor + ", \"a\", \"b\")"));
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
                .isEqualTo("(c[\"metadata\"][\"tenant\"] = \"acme\" AND ((NOT " + expected
                        + ") OR c[\"status\"] = \"active\"))");
        assertThat(mapper.map(new And(filter, filter))).isEqualTo("(" + expected + " AND " + expected + ")");
        assertThat(mapper.map(new Or(filter, filter))).isEqualTo("(" + expected + " OR " + expected + ")");
    }

    @ParameterizedTest
    @ValueSource(strings = {"user-id", "my key", "kategorie_über", "value", "order", "a/b", "1leading"})
    void map_supportsKeysThatTheBarePropertyAccessorCannotExpress(String key) {
        String result = mapper.map(new IsEqualTo(key, "v"));
        assertThat(result).isEqualTo("c[\"" + key + "\"] = \"v\"");
    }

    @Test
    void map_escapesDoubleQuoteInKey() {
        String result = mapper.map(new IsEqualTo("a\"b", "v"));
        assertThat(result).isEqualTo("c[\"a\\\"b\"] = \"v\"");
    }

    @Test
    void map_escapesBackslashInKey() {
        String result = mapper.map(new IsEqualTo("a\\b", "v"));
        assertThat(result).isEqualTo("c[\"a\\\\b\"] = \"v\"");
    }

    @Test
    void map_keepsDottedKeyAddressingANestedProperty() {
        String result = mapper.map(new IsEqualTo("metadata.category", "electronics"));
        assertThat(result).isEqualTo("c[\"metadata\"][\"category\"] = \"electronics\"");
    }

    @Test
    void map_escapesControlCharactersInValue() {
        String result = mapper.map(new IsEqualTo("summary", "line one\nline two"));
        assertThat(result).isEqualTo("c[\"summary\"] = \"line one\\u000aline two\"");
    }

    @Test
    void map_escapesControlCharactersInKey() {
        String result = mapper.map(new IsEqualTo("a\tb", "v"));
        assertThat(result).isEqualTo("c[\"a\\u0009b\"] = \"v\"");
    }

    @Test
    void map_escapesNulCharacter() {
        String result = mapper.map(new IsEqualTo("a" + ((char) 0) + "b", "v"));
        assertThat(result).isEqualTo("c[\"a\\u0000b\"] = \"v\"");
    }

    static IntStream controlCharacters() {
        return IntStream.range(0, 0x20);
    }

    @ParameterizedTest
    @MethodSource("controlCharacters")
    void map_escapesEveryControlCharacterInKeysAndValues(int character) {
        String text = "a" + (char) character + "b";
        String escaped = String.format("a\\u%04xb", character);

        assertThat(mapper.map(new IsEqualTo(text, text))).isEqualTo("c[\"" + escaped + "\"] = \"" + escaped + "\"");
    }

    @Test
    void map_escapesValuesAcrossAllFilterTypes() {
        String value = "a\\\"\n";
        String literal = "\"a\\\\\\\"\\u000a\"";

        assertThat(mapper.map(new IsEqualTo("key", value))).isEqualTo("c[\"key\"] = " + literal);
        assertThat(mapper.map(new IsNotEqualTo("key", value))).isEqualTo("(NOT c[\"key\"] = " + literal + ")");
        assertThat(mapper.map(new IsGreaterThan("key", value))).isEqualTo("c[\"key\"] > " + literal);
        assertThat(mapper.map(new IsGreaterThanOrEqualTo("key", value))).isEqualTo("c[\"key\"] >= " + literal);
        assertThat(mapper.map(new IsLessThan("key", value))).isEqualTo("c[\"key\"] < " + literal);
        assertThat(mapper.map(new IsLessThanOrEqualTo("key", value))).isEqualTo("c[\"key\"] <= " + literal);
        assertThat(mapper.map(new IsIn("key", Arrays.asList(value)))).isEqualTo("c[\"key\"] IN (" + literal + ")");
        assertThat(mapper.map(new IsNotIn("key", Arrays.asList(value))))
                .isEqualTo("(NOT c[\"key\"] IN (" + literal + "))");
        assertThat(mapper.map(new ContainsString("key", value))).isEqualTo("CONTAINS(c[\"key\"], " + literal + ")");
        assertThat(mapper.map(new FullTextContains("key", value)))
                .isEqualTo("FullTextContains(c[\"key\"], " + literal + ")");
        assertThat(mapper.map(new FullTextContainsAll("key", value, value)))
                .isEqualTo("FullTextContainsAll(c[\"key\"], " + literal + ", " + literal + ")");
        assertThat(mapper.map(new FullTextContainsAny("key", value, value)))
                .isEqualTo("FullTextContainsAny(c[\"key\"], " + literal + ", " + literal + ")");
    }
}
