package dev.langchain4j.store.embedding.oracle.vecdb.mapper;

import static dev.langchain4j.store.embedding.filter.Filter.and;
import static dev.langchain4j.store.embedding.filter.Filter.not;
import static dev.langchain4j.store.embedding.filter.Filter.or;
import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.comparison.IsEqualTo;
import dev.langchain4j.store.embedding.filter.comparison.IsGreaterThan;
import dev.langchain4j.store.embedding.oracle.vecdb.enums.VecDbApiVersion;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Verifies constrained translation of LangChain4j metadata filters into version-aware VecDB QBE. */
class VecDbFiltersTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /** Verifies that an absent LangChain4j filter produces no VecDB filter document. */
    @Test
    void testReturnsNullWhenFilterIsNotConfigured() {
        assertThat(toJson(null)).isNull();
        assertThatCode(() -> VecDbFilters.validate(null, VecDbApiVersion.V23_26_3))
                .doesNotThrowAnyException();
    }

    /** Verifies QBE translation for every supported scalar and ordered comparison operator. */
    @ParameterizedTest
    @MethodSource("comparisonOperators")
    void testMapsComparisonOperators(Filter filter, String expectedJson) throws JsonProcessingException {
        assertJsonEquals(toJson(filter), expectedJson);
    }

    static Stream<Arguments> comparisonOperators() {
        return Stream.of(
                Arguments.of(metadataKey("name").isEqualTo("Alice"), "{\"name\":{\"$eq\":\"Alice\"}}"),
                Arguments.of(metadataKey("name").isNotEqualTo("Alice"), "{\"name\":{\"$ne\":\"Alice\"}}"),
                Arguments.of(metadataKey("age").isGreaterThan(18), "{\"age\":{\"$gt\":18}}"),
                Arguments.of(metadataKey("age").isGreaterThanOrEqualTo(18), "{\"age\":{\"$gte\":18}}"),
                Arguments.of(metadataKey("age").isLessThan(65), "{\"age\":{\"$lt\":65}}"),
                Arguments.of(metadataKey("age").isLessThanOrEqualTo(65), "{\"age\":{\"$lte\":65}}"),
                Arguments.of(metadataKey("tenant").isIn("acme"), "{\"tenant\":{\"$in\":[\"acme\"]}}"),
                Arguments.of(metadataKey("tenant").isNotIn("acme"), "{\"tenant\":{\"$nin\":[\"acme\"]}}"));
    }

    /** Verifies that UUID metadata operands are serialized as their string representation. */
    @Test
    void testNormalizesUuidValuesToStrings() throws JsonProcessingException {
        UUID tenantId = UUID.fromString("b597e89c-7260-4b72-b5ad-14b1151d6367");

        assertJsonEquals(toJson(metadataKey("tenantId").isEqualTo(tenantId)), """
                {
                  "tenantId": {
                    "$eq": "b597e89c-7260-4b72-b5ad-14b1151d6367"
                  }
                }
                """);
    }

    /** Verifies recursive translation of LangChain4j AND and OR expressions. */
    @Test
    void testMapsAndOrExpressions() throws JsonProcessingException {
        Filter filter = and(
                metadataKey("tenant").isEqualTo("acme"),
                or(
                        metadataKey("year").isGreaterThanOrEqualTo(2024),
                        metadataKey("category").isEqualTo("guide")));

        assertJsonEquals(toJson(filter), """
                {
                  "$and": [
                    {"tenant": {"$eq": "acme"}},
                    {
                      "$or": [
                        {"year": {"$gte": 2024}},
                        {"category": {"$eq": "guide"}}
                      ]
                    }
                  ]
                }
                """);
    }

    /** Verifies equality negation without adding an unnecessary missing-field condition. */
    @Test
    void testNegatesEqualityWithoutMissingFieldBranch() throws JsonProcessingException {
        assertJsonEquals(toJson(not(metadataKey("tenant").isEqualTo("acme"))), """
                {
                  "tenant": {
                    "$ne": "acme"
                  }
                }
                """);
    }

    /** Verifies that negated ordered comparisons include metadata records where the field is absent. */
    @Test
    void testNegatesOrderedComparisonWithMissingFieldBranch() throws JsonProcessingException {
        assertJsonEquals(toJson(not(metadataKey("age").isGreaterThan(18))), """
                {
                  "$or": [
                    {"age": {"$lte": 18}},
                    {"age": {"$exists": false}}
                  ]
                }
                """);
    }

    /** Verifies De Morgan translation when a logical AND expression is negated. */
    @Test
    void testAppliesDeMorganLawWhenNegatingAnd() throws JsonProcessingException {
        Filter filter = not(
                and(metadataKey("tenant").isEqualTo("acme"), metadataKey("year").isGreaterThan(2020)));

        assertJsonEquals(toJson(filter), """
                {
                  "$or": [
                    {"tenant": {"$ne": "acme"}},
                    {
                      "$or": [
                        {"year": {"$lte": 2020}},
                        {"year": {"$exists": false}}
                      ]
                    }
                  ]
                }
                """);
    }

    /** Verifies De Morgan translation when a logical OR expression is negated. */
    @Test
    void testAppliesDeMorganLawWhenNegatingOr() throws JsonProcessingException {
        Filter filter = not(or(
                metadataKey("tenant").isEqualTo("acme"), metadataKey("category").isEqualTo("guide")));

        assertJsonEquals(toJson(filter), """
                {
                  "$and": [
                    {"tenant": {"$ne": "acme"}},
                    {"category": {"$ne": "guide"}}
                  ]
                }
                """);
    }

    /** Verifies that two nested NOT filters restore the original comparison. */
    @Test
    void testDoubleNegationRestoresOriginalOperator() throws JsonProcessingException {
        assertJsonEquals(toJson(not(not(metadataKey("tenant").isEqualTo("acme")))), "{\"tenant\":{\"$eq\":\"acme\"}}");
    }

    /** Verifies the validation-only path for a supported filter expression. */
    @Test
    void testValidateAcceptsSupportedFilter() {
        assertThatCode(() -> VecDbFilters.validate(
                        metadataKey("year").isGreaterThanOrEqualTo(2024), VecDbApiVersion.V23_26_3))
                .doesNotThrowAnyException();
    }

    /** Verifies that {@code ContainsString} maps to {@code $hasSubstring} for the newer API. */
    @Test
    void testMapsContainsStringForNewApi() throws JsonProcessingException {
        assertJsonEquals(
                VecDbFilters.toJson(metadataKey("title").containsString("Oracle"), VecDbApiVersion.V23_26_3),
                "{\"title\":{\"$hasSubstring\":\"Oracle\"}}");
    }

    /** Verifies negated substring mapping for the newer QBE implementation. */
    @Test
    void testMapsNegatedContainsStringForNewApi() throws JsonProcessingException {
        assertJsonEquals(
                VecDbFilters.toJson(not(metadataKey("title").containsString("Oracle")), VecDbApiVersion.V23_26_3),
                "{\"title\":{\"$not\":{\"$hasSubstring\":\"Oracle\"}}}");
    }

    /** Verifies that substring filtering is rejected for API versions that do not support it. */
    @Test
    void testRejectsContainsStringForLegacyApi() {
        assertThatThrownBy(() ->
                        VecDbFilters.toJson(metadataKey("title").containsString("Oracle"), VecDbApiVersion.V23_26_1))
                .isInstanceOf(UnsupportedFeatureException.class)
                .hasMessageContaining("ContainsString")
                .hasMessageContaining("23.26.3 or later");
    }

    /** Verifies that an empty substring is preserved because Java strings always contain it. */
    @Test
    void testMapsEmptyContainsStringForNewApi() throws JsonProcessingException {
        assertJsonEquals(
                VecDbFilters.toJson(metadataKey("title").containsString(""), VecDbApiVersion.V23_26_3),
                "{\"title\":{\"$hasSubstring\":\"\"}}");
    }

    /** Verifies that the reserved compatibility text property remains available for filtering. */
    @Test
    void testMapsTextMetadataKey() throws JsonProcessingException {
        assertJsonEquals(toJson(metadataKey("text").isEqualTo("segment")), "{\"text\":{\"$eq\":\"segment\"}}");
    }

    /** Verifies rejection of keys that VecDB QBE would interpret as operators or JSON paths. */
    @ParameterizedTest
    @ValueSource(strings = {"$tenant", "author.country", "items[0]", "items]", "`tenant`"})
    void testRejectsNestedOrQbeMetadataKeys(String key) {
        assertThatThrownBy(() -> toJson(metadataKey(key).isEqualTo("value")))
                .isInstanceOf(UnsupportedFeatureException.class)
                .hasMessageContaining("nested or QBE operator paths are not supported");
    }

    /** Verifies rejection of Boolean operands outside LangChain4j's supported metadata value types. */
    @Test
    void testRejectsBooleanScalarValue() {
        assertThatThrownBy(() -> toJson(new IsEqualTo("active", true)))
                .isInstanceOf(UnsupportedFeatureException.class)
                .hasMessageContaining("metadata value type java.lang.Boolean");
    }

    /** Verifies that Boolean values cannot be used with ordered comparisons. */
    @Test
    void testRejectsBooleanOrderedValue() {
        assertThatThrownBy(() -> toJson(new IsGreaterThan("active", true)))
                .isInstanceOf(UnsupportedFeatureException.class)
                .hasMessageContaining("$gt requires a LangChain4j string or numeric metadata value");
    }

    /** Verifies rejection of non-finite numbers that cannot be represented safely in JSON. */
    @Test
    void testRejectsNonFiniteNumericValue() {
        assertThatThrownBy(() -> toJson(new IsEqualTo("score", Double.NaN)))
                .isInstanceOf(UnsupportedFeatureException.class)
                .hasMessageContaining("non-finite floating-point metadata value NaN");
    }

    private static void assertJsonEquals(String actual, String expected) throws JsonProcessingException {
        assertThat(readJson(actual)).isEqualTo(readJson(expected));
    }

    private static String toJson(Filter filter) {
        return VecDbFilters.toJson(filter, VecDbApiVersion.V23_26_3);
    }

    private static JsonNode readJson(String json) throws JsonProcessingException {
        return OBJECT_MAPPER.readTree(json);
    }
}
