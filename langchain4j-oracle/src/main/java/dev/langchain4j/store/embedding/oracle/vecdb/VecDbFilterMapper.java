package dev.langchain4j.store.embedding.oracle.vecdb;

import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.langchain4j.exception.UnsupportedFeatureException;
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
import java.util.Collection;
import java.util.UUID;

/**
 * Maps LangChain4j metadata filters to the JSON accepted by {@code DBMS_VECTOR_DATABASE.SEARCH}.
 */
final class VecDbFilterMapper {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private VecDbFilterMapper() {}

    /**
     * Returns a VecDB metadata filter, or {@code null} when no filter is configured.
     */
    static String toJson(Filter filter) {
        return filter == null ? null : toJsonNode(filter, false).toString();
    }

    private static JsonNode toJsonNode(Filter filter, boolean negated) {
        if (filter instanceof IsEqualTo equal) {
            return comparison(equal.key(), operator("$eq", negated), primitive(equal.comparisonValue()));
        }
        if (filter instanceof IsNotEqualTo notEqual) {
            return comparison(notEqual.key(), operator("$ne", negated), primitive(notEqual.comparisonValue()));
        }
        if (filter instanceof IsGreaterThan greaterThan) {
            return numericComparison(
                    greaterThan.key(), operator("$gt", negated), greaterThan.comparisonValue());
        }
        if (filter instanceof IsGreaterThanOrEqualTo greaterThanOrEqualTo) {
            return numericComparison(
                    greaterThanOrEqualTo.key(),
                    operator("$gte", negated),
                    greaterThanOrEqualTo.comparisonValue());
        }
        if (filter instanceof IsLessThan lessThan) {
            return numericComparison(lessThan.key(), operator("$lt", negated), lessThan.comparisonValue());
        }
        if (filter instanceof IsLessThanOrEqualTo lessThanOrEqualTo) {
            return numericComparison(
                    lessThanOrEqualTo.key(), operator("$lte", negated), lessThanOrEqualTo.comparisonValue());
        }
        if (filter instanceof IsIn in) {
            return collectionComparison(in.key(), operator("$in", negated), in.comparisonValues());
        }
        if (filter instanceof IsNotIn notIn) {
            return collectionComparison(notIn.key(), operator("$nin", negated), notIn.comparisonValues());
        }
        if (filter instanceof And and) {
            return logical(negated ? "$or" : "$and", and.left(), and.right(), negated);
        }
        if (filter instanceof Or or) {
            return logical(negated ? "$and" : "$or", or.left(), or.right(), negated);
        }
        if (filter instanceof Not not) {
            return toJsonNode(not.expression(), !negated);
        }

        throw new UnsupportedFeatureException(
                "Unsupported VecDB filter: " + filter.getClass().getName());
    }

    private static ObjectNode comparison(String key, String operator, JsonNode value) {
        ensureNotBlank(key, "filter key");

        ObjectNode operation = OBJECT_MAPPER.createObjectNode();
        operation.set(operator, value);

        ObjectNode comparison = OBJECT_MAPPER.createObjectNode();
        comparison.set(key, operation);
        return comparison;
    }

    private static ObjectNode numericComparison(String key, String operator, Object value) {
        if (!(value instanceof Number)) {
            throw new UnsupportedFeatureException(operator + " requires a numeric value for VecDB");
        }
        return comparison(key, operator, OBJECT_MAPPER.valueToTree(value));
    }

    private static ObjectNode collectionComparison(String key, String operator, Collection<?> values) {
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException(operator + " requires at least one value");
        }

        ArrayNode array = OBJECT_MAPPER.createArrayNode();
        for (Object value : values) {
            array.add(primitive(value));
        }
        return comparison(key, operator, array);
    }

    private static ObjectNode logical(
            String operator, Filter left, Filter right, boolean negateChildren) {
        ArrayNode operands = OBJECT_MAPPER.createArrayNode();
        operands.add(toJsonNode(left, negateChildren));
        operands.add(toJsonNode(right, negateChildren));

        ObjectNode logical = OBJECT_MAPPER.createObjectNode();
        logical.set(operator, operands);
        return logical;
    }

    private static String operator(String operator, boolean negated) {
        if (!negated) {
            return operator;
        }

        return switch (operator) {
            case "$eq" -> "$ne";
            case "$ne" -> "$eq";
            case "$gt" -> "$lte";
            case "$gte" -> "$lt";
            case "$lt" -> "$gte";
            case "$lte" -> "$gt";
            case "$in" -> "$nin";
            case "$nin" -> "$in";
            default -> throw new UnsupportedFeatureException("Cannot negate VecDB operator: " + operator);
        };
    }

    private static JsonNode primitive(Object value) {
        if (value instanceof UUID uuid) {
            return OBJECT_MAPPER.valueToTree(uuid.toString());
        }
        if (value instanceof String || value instanceof Number || value instanceof Boolean) {
            return OBJECT_MAPPER.valueToTree(value);
        }

        throw new UnsupportedFeatureException(
                "Unsupported VecDB filter value: " + (value == null ? "null" : value.getClass().getName()));
    }
}
