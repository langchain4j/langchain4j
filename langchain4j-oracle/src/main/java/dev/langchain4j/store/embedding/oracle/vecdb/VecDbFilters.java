package dev.langchain4j.store.embedding.oracle.vecdb;

import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.store.embedding.oracle.vecdb.VecDbVectorJsonMapper.TEXT_METADATA_KEY;

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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Factory for constrained LangChain4j-to-VecDB QBE filter translators. */
final class VecDbFilters {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final FilterRule EQUAL = new FilterRule("$eq", "$ne", OperandKind.SCALAR, false);
    private static final FilterRule NOT_EQUAL = new FilterRule("$ne", "$eq", OperandKind.SCALAR, false);
    private static final FilterRule GREATER_THAN = new FilterRule("$gt", "$lte", OperandKind.ORDERED, true);
    private static final FilterRule GREATER_THAN_OR_EQUAL = new FilterRule("$gte", "$lt", OperandKind.ORDERED, true);
    private static final FilterRule LESS_THAN = new FilterRule("$lt", "$gte", OperandKind.ORDERED, true);
    private static final FilterRule LESS_THAN_OR_EQUAL = new FilterRule("$lte", "$gt", OperandKind.ORDERED, true);
    private static final FilterRule IN = new FilterRule("$in", "$nin", OperandKind.COLLECTION, false);
    private static final FilterRule NOT_IN = new FilterRule("$nin", "$in", OperandKind.COLLECTION, false);

    private static final Map<Class<? extends Filter>, FilterTranslator<?>> TRANSLATORS;

    static {
        Map<Class<? extends Filter>, FilterTranslator<?>> translators = new HashMap<>();

        register(
                translators,
                IsEqualTo.class,
                (filter, context) -> comparison(filter.key(), filter.comparisonValue(), EQUAL, context));
        register(
                translators,
                IsNotEqualTo.class,
                (filter, context) -> comparison(filter.key(), filter.comparisonValue(), NOT_EQUAL, context));
        register(
                translators,
                IsGreaterThan.class,
                (filter, context) -> comparison(filter.key(), filter.comparisonValue(), GREATER_THAN, context));
        register(
                translators,
                IsGreaterThanOrEqualTo.class,
                (filter, context) ->
                        comparison(filter.key(), filter.comparisonValue(), GREATER_THAN_OR_EQUAL, context));
        register(
                translators,
                IsLessThan.class,
                (filter, context) -> comparison(filter.key(), filter.comparisonValue(), LESS_THAN, context));
        register(
                translators,
                IsLessThanOrEqualTo.class,
                (filter, context) -> comparison(filter.key(), filter.comparisonValue(), LESS_THAN_OR_EQUAL, context));
        register(
                translators,
                IsIn.class,
                (filter, context) -> comparison(filter.key(), filter.comparisonValues(), IN, context));
        register(
                translators,
                IsNotIn.class,
                (filter, context) -> comparison(filter.key(), filter.comparisonValues(), NOT_IN, context));
        register(translators, And.class, VecDbFilters::and);
        register(translators, Or.class, VecDbFilters::or);
        register(translators, Not.class, (filter, context) -> context.negate().translate(filter.expression()));

        TRANSLATORS = Collections.unmodifiableMap(translators);
    }

    private VecDbFilters() {}

    /** Returns a VecDB metadata filter, or {@code null} when no filter is configured. */
    static String toJson(Filter filter) {
        return filter == null
                ? null
                : TranslationContext.root().translate(filter).toString();
    }

    /** Validates that a filter can be translated using the supported VecDB metadata-filter contract. */
    static void validate(Filter filter) {
        if (filter != null) {
            TranslationContext.root().translate(filter);
        }
    }

    private static JsonNode translate(Filter filter, TranslationContext context) {
        FilterTranslator<?> translator = TRANSLATORS.get(filter.getClass());
        if (translator == null) {
            throw unsupported("filter type " + filter.getClass().getName());
        }
        return invoke(translator, filter, context);
    }

    private static <F extends Filter> void register(
            Map<Class<? extends Filter>, FilterTranslator<?>> translators,
            Class<F> filterType,
            FilterTranslator<F> translator) {
        translators.put(filterType, translator);
    }

    @SuppressWarnings("unchecked")
    private static <F extends Filter> JsonNode invoke(
            FilterTranslator<?> translator, F filter, TranslationContext context) {
        return ((FilterTranslator<F>) translator).translate(filter, context);
    }

    private static JsonNode comparison(String key, Object value, FilterRule rule, TranslationContext context) {
        String operator = rule.operator(context.negated());
        JsonNode operand =
                switch (rule.operandKind()) {
                    case SCALAR -> OBJECT_MAPPER.valueToTree(scalar(value));
                    case ORDERED -> OBJECT_MAPPER.valueToTree(orderedScalar(value, operator));
                    case COLLECTION -> OBJECT_MAPPER.valueToTree(collection((Collection<?>) value, operator));
                };

        ObjectNode condition = condition(key, operator, operand);
        if (!context.negated() || !rule.includeMissingWhenNegated()) {
            return condition;
        }

        return logical("$or", condition, condition(key, "$exists", OBJECT_MAPPER.valueToTree(false)));
    }

    private static JsonNode and(And filter, TranslationContext context) {
        return logical(
                context.negated() ? "$or" : "$and",
                context.translate(filter.left()),
                context.translate(filter.right()));
    }

    private static JsonNode or(Or filter, TranslationContext context) {
        return logical(
                context.negated() ? "$and" : "$or",
                context.translate(filter.left()),
                context.translate(filter.right()));
    }

    private static ObjectNode condition(String key, String operator, JsonNode operand) {
        ObjectNode operation = OBJECT_MAPPER.createObjectNode();
        operation.set(operator, operand);

        ObjectNode condition = OBJECT_MAPPER.createObjectNode();
        condition.set(key(key), operation);
        return condition;
    }

    private static ObjectNode logical(String operator, JsonNode left, JsonNode right) {
        ArrayNode operands = OBJECT_MAPPER.createArrayNode();
        operands.add(left);
        operands.add(right);

        ObjectNode logical = OBJECT_MAPPER.createObjectNode();
        logical.set(operator, operands);
        return logical;
    }

    private static String key(String key) {
        key = ensureNotBlank(key, "filter key");
        if (TEXT_METADATA_KEY.equals(key)) {
            throw unsupported("reserved metadata key \"" + TEXT_METADATA_KEY + "\"");
        }
        if (key.startsWith("$")
                || key.indexOf('.') >= 0
                || key.indexOf('[') >= 0
                || key.indexOf(']') >= 0
                || key.indexOf('`') >= 0) {
            throw unsupported("metadata key \"" + key + "\" because nested or QBE operator paths are not supported");
        }
        return key;
    }

    private static Object scalar(Object value) {
        if (value instanceof UUID uuid) {
            return uuid.toString();
        }
        if (value instanceof String || value instanceof Integer || value instanceof Long) {
            return value;
        }
        if (value instanceof Float number) {
            if (!Float.isFinite(number)) {
                throw unsupported("non-finite floating-point metadata value " + value);
            }
            return value;
        }
        if (value instanceof Double number) {
            if (!Double.isFinite(number)) {
                throw unsupported("non-finite floating-point metadata value " + value);
            }
            return value;
        }

        throw unsupported("metadata value type "
                + (value == null ? "null" : value.getClass().getName()));
    }

    private static Object orderedScalar(Object value, String operator) {
        if (!(value instanceof String) && !(value instanceof Number)) {
            throw unsupported(operator + " requires a LangChain4j string or numeric metadata value");
        }
        return scalar(value);
    }

    private static List<Object> collection(Collection<?> values, String operator) {
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException(operator + " requires at least one value");
        }

        List<Object> normalized = new ArrayList<>(values.size());
        for (Object value : values) {
            normalized.add(scalar(value));
        }
        return List.copyOf(normalized);
    }

    private static UnsupportedFeatureException unsupported(String detail) {
        return new UnsupportedFeatureException("Unsupported VecDB metadata filter " + detail);
    }

    @FunctionalInterface
    private interface FilterTranslator<F extends Filter> {

        JsonNode translate(F filter, TranslationContext context);
    }

    private static final class TranslationContext {

        private static final TranslationContext ROOT = new TranslationContext(false);
        private static final TranslationContext NEGATED = new TranslationContext(true);

        private final boolean negated;

        private TranslationContext(boolean negated) {
            this.negated = negated;
        }

        private static TranslationContext root() {
            return ROOT;
        }

        private boolean negated() {
            return negated;
        }

        private TranslationContext negate() {
            return negated ? ROOT : NEGATED;
        }

        private JsonNode translate(Filter filter) {
            return VecDbFilters.translate(filter, this);
        }
    }

    private record FilterRule(
            String operator, String negatedOperator, OperandKind operandKind, boolean includeMissingWhenNegated) {

        private String operator(boolean negated) {
            return negated ? negatedOperator : operator;
        }
    }

    private enum OperandKind {
        SCALAR,
        ORDERED,
        COLLECTION
    }
}
