package dev.langchain4j.store.embedding.redis;

import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.comparison.*;
import dev.langchain4j.store.embedding.filter.logical.And;
import dev.langchain4j.store.embedding.filter.logical.Not;
import dev.langchain4j.store.embedding.filter.logical.Or;
import redis.clients.jedis.search.schemafields.NumericField;
import redis.clients.jedis.search.schemafields.SchemaField;
import redis.clients.jedis.search.schemafields.TagField;
import redis.clients.jedis.search.schemafields.TextField;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * TODO: document
 */
class RedisMetadataFilterMapper {

    private static final String FILTER_PREFIX = "@";
    private static final String NOT_PREFIX = "-";
    private static final String OR_DELIMITER = " | ";

    private final Map<String, SchemaField> schemaFieldMap;

    RedisMetadataFilterMapper(Map<String, SchemaField> schemaFieldMap) {
        this.schemaFieldMap = schemaFieldMap;
    }

    String mapToFilter(Filter filter) {
        if (filter == null) {
            return "(*)";
        }

        if (filter instanceof IsEqualTo) {
            return mapEqual((IsEqualTo) filter);
        } else if (filter instanceof IsNotEqualTo) {
            return mapNotEqual((IsNotEqualTo) filter);
        } else if (filter instanceof IsGreaterThan) {
            return mapGreaterThan((IsGreaterThan) filter);
        } else if (filter instanceof IsGreaterThanOrEqualTo) {
            return mapGreaterThanOrEqual((IsGreaterThanOrEqualTo) filter);
        } else if (filter instanceof IsLessThan) {
            return mapLessThan((IsLessThan) filter);
        } else if (filter instanceof IsLessThanOrEqualTo) {
            return mapLessThanOrEqual((IsLessThanOrEqualTo) filter);
        } else if (filter instanceof IsIn) {
            return mapIn((IsIn) filter);
        } else if (filter instanceof IsNotIn) {
            return mapNotIn((IsNotIn) filter);
        } else if (filter instanceof And) {
            return mapAnd((And) filter);
        } else if (filter instanceof Not) {
            return mapNot((Not) filter);
        } else if (filter instanceof Or) {
            return mapOr((Or) filter);
        } else {
            throw new UnsupportedOperationException("Unsupported filter type: " + filter.getClass().getName());
        }
    }

    /**
     * Numeric: @key:[value]
     * Tag: @key:{value}
     * Text: @key:\"value\"
     */
    String mapEqual(IsEqualTo filter) {
        return doMapEqual(filter.key(), filter.comparisonValue());
    }

    /**
     * Numeric: -@key:[value]
     * Tag: -@key:{value}
     * Text: -@key:\"value\"
     */
    String mapNotEqual(IsNotEqualTo filter) {
        return NOT_PREFIX + String.format("(%s)", doMapEqual(filter.key(), filter.comparisonValue()));
    }

    String mapGreaterThan(IsGreaterThan filter) {
        Numeric value = Numeric.constructNumeric(filter.comparisonValue(), true);
        return doMapCompare(filter.key(), value.toString(), Numeric.POSITIVE_INFINITY.toString());
    }

    String mapGreaterThanOrEqual(IsGreaterThanOrEqualTo filter) {
        Numeric value = Numeric.constructNumeric(filter.comparisonValue(), false);
        return doMapCompare(filter.key(), value.toString(), Numeric.POSITIVE_INFINITY.toString());
    }

    String mapLessThan(IsLessThan filter) {
        Numeric value = Numeric.constructNumeric(filter.comparisonValue(), true);
        return doMapCompare(filter.key(), Numeric.NEGATIVE_INFINITY.toString(), value.toString());
    }

    String mapLessThanOrEqual(IsLessThanOrEqualTo filter) {
        Numeric value = Numeric.constructNumeric(filter.comparisonValue(), false);
        return doMapCompare(filter.key(), Numeric.NEGATIVE_INFINITY.toString(), value.toString());
    }

    String mapIn(IsIn filter) {
        return doMapIn(filter.key(), filter.comparisonValues());
    }

    String mapNotIn(IsNotIn filter) {
        return NOT_PREFIX + doMapIn(filter.key(), filter.comparisonValues());
    }

    String mapAnd(And filter) {
        // @filter1 @filter2
        return "(" + mapToFilter(filter.left()) + " " + mapToFilter(filter.right()) + ")";
    }

    String mapNot(Not filter) {
        // -@filter
        return NOT_PREFIX + "(" + mapToFilter(filter.expression()) + ")";
    }

    String mapOr(Or filter) {
        // @filter1 | @filter2
        return "(" + mapToFilter(filter.left()) + OR_DELIMITER + mapToFilter(filter.right()) + ")";
    }

    private String doMapEqual(String key, Object value) {
        SchemaField fieldType = schemaFieldMap.getOrDefault(key, TagField.of(key));

        String keyPrefix = toKeyPrefix(key);
        if (fieldType instanceof NumericField) {
            return keyPrefix + Boundary.NUMERIC_BOUNDARY.toSingleString(value);
        } else if (fieldType instanceof TagField) {
            return keyPrefix + Boundary.TAG_BOUNDARY.toSingleString(value);
        } else if (fieldType instanceof TextField) {
            return keyPrefix + Boundary.TEXT_BOUNDARY.toSingleString(value);
        } else {
            throw new UnsupportedOperationException("Unsupported field type: " + fieldType);
        }
    }

    private String doMapCompare(String key, String leftValue, String rightValue) {
        SchemaField fieldType = schemaFieldMap.getOrDefault(key, TextField.of(key));

        if (fieldType instanceof NumericField) {
            return toKeyPrefix(key) + Boundary.NUMERIC_BOUNDARY.toRangeString(leftValue, rightValue);
        } else {
            throw new UnsupportedOperationException("Redis do not support non-Numeric range search, fieldType: " + fieldType);
        }
    }

    private String doMapIn(String key, Collection<?> values) {
        SchemaField fieldType = schemaFieldMap.getOrDefault(key, TagField.of(key));

        String keyPrefix = toKeyPrefix(key);
        if (fieldType instanceof TagField) {
            String inFilter = values.stream()
                    .map(Object::toString)
                    .collect(Collectors.joining(OR_DELIMITER));

            return keyPrefix + Boundary.TAG_BOUNDARY.toSingleString(inFilter);
        } else if (fieldType instanceof TextField) {
            String inFilter = values.stream()
                    .map(Boundary.TEXT_BOUNDARY::toSingleString)
                    .collect(Collectors.joining(OR_DELIMITER));

            return keyPrefix + Boundary.TEXT_IN_BOUNDARY.toSingleString(inFilter);
        } else {
            throw new UnsupportedOperationException("Redis do not support NumericType \"in\" search, fieldType: " + fieldType);
        }
    }

    private String toKeyPrefix(String key) {
        return FILTER_PREFIX + key + ":";
    }

    static class Numeric {

        static final Numeric POSITIVE_INFINITY = new Numeric(Double.POSITIVE_INFINITY, true);
        static final Numeric NEGATIVE_INFINITY = new Numeric(Double.NEGATIVE_INFINITY, true);

        private static final String INFINITY = "inf";
        private static final String MINUS_INFINITY = "-inf";
        private static final String INCLUSIVE_FORMAT = "%s";
        private static final String EXCLUSIVE_FORMAT = "(%s";

        private final Object value;
        private final boolean exclusive;

        Numeric(Object value, boolean exclusive) {
            this.value = value;
            this.exclusive = exclusive;
        }

        static Numeric constructNumeric(Object value, boolean exclusive) {
            return new Numeric(value, exclusive);
        }

        @Override
        public String toString() {
            if (this == POSITIVE_INFINITY) {
                return INFINITY;
            } else if (this == NEGATIVE_INFINITY) {
                return MINUS_INFINITY;
            }

            return String.format(formatString(), value);
        }

        private String formatString() {
            if (exclusive) {
                return EXCLUSIVE_FORMAT;
            }
            return INCLUSIVE_FORMAT;
        }
    }

    static class Boundary {

        static final Boundary TAG_BOUNDARY = new Boundary("{", "}");
        static final Boundary TEXT_BOUNDARY = new Boundary("\"", "\"");
        static final Boundary TEXT_IN_BOUNDARY = new Boundary("(", ")");
        static final Boundary NUMERIC_BOUNDARY = new Boundary("[", "]");

        private final String left;
        private final String right;

        Boundary(String left, String right) {
            this.left = left;
            this.right = right;
        }

        String toSingleString(Object value) {
            return String.format("%s%s%s", left, value, right);
        }

        String toRangeString(Object leftValue, Object rightValue) {
            return String.format("%s%s %s%s", left, leftValue, rightValue, right);
        }
    }
}
