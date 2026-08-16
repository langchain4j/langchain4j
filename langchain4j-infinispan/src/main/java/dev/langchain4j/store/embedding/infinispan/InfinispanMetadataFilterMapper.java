package dev.langchain4j.store.embedding.infinispan;

import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
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
import java.util.stream.Collectors;

/**
 * Class used in a local methods of
 * {@link InfinispanEmbeddingStore#search(EmbeddingSearchRequest)}
 * {@link InfinispanEmbeddingStore#removeAll(Filter)}
 */
class InfinispanMetadataFilterMapper {
    private int i = -1;

    class FilterResult {
        String join;
        String query;

        public FilterResult(String query) {
            this.query = query;
            StringBuilder sb = new StringBuilder();
            for (int j = 0; j <= i; j++) {
                sb.append(" join i.metadata m").append(j);
            }
            this.join = sb.toString();
        }
    }

    FilterResult map(Filter filter) {
        String filterQuery = "";
        if (filter == null) {
            return null;
        }
        if (filter instanceof IsEqualTo) {
            i++;
            filterQuery = mapEqual((IsEqualTo) filter);
        } else if (filter instanceof IsNotEqualTo) {
            i++;
            filterQuery = mapNotEqual((IsNotEqualTo) filter);
        } else if (filter instanceof IsGreaterThan) {
            i++;
            filterQuery = mapGreaterThan((IsGreaterThan) filter);
        } else if (filter instanceof IsGreaterThanOrEqualTo) {
            i++;
            filterQuery = mapGreaterThanOrEqual((IsGreaterThanOrEqualTo) filter);
        } else if (filter instanceof IsLessThan) {
            i++;
            filterQuery = mapLessThan((IsLessThan) filter);
        } else if (filter instanceof IsLessThanOrEqualTo) {
            i++;
            filterQuery = mapLessThanOrEqual((IsLessThanOrEqualTo) filter);
        } else if (filter instanceof IsIn) {
            i++;
            filterQuery = mapIn((IsIn) filter);
        } else if (filter instanceof IsNotIn) {
            i++;
            filterQuery = mapNotIn((IsNotIn) filter);
        } else if (filter instanceof And) {
            filterQuery = mapAnd((And) filter);
        } else if (filter instanceof Not) {
            filterQuery = mapNot((Not) filter);
        } else if (filter instanceof Or) {
            filterQuery = mapOr((Or) filter);
        } else {
            throw new UnsupportedOperationException(
                    "Unsupported filter type: " + filter.getClass().getName());
        }

        return new FilterResult(filterQuery);
    }

    private String mapEqual(IsEqualTo filter) {
        return metadataKey(filter.key()) + computeFilter("=", filter.comparisonValue());
    }

    private String mapNotEqual(IsNotEqualTo filter) {
        return computeFilter("!=", filter.comparisonValue()) + metadataKeyLast(filter.key()) + addMetadataNullCheck();
    }

    private String mapGreaterThan(IsGreaterThan filter) {
        return metadataKey(filter.key()) + computeFilter(">", filter.comparisonValue());
    }

    private String mapGreaterThanOrEqual(IsGreaterThanOrEqualTo filter) {
        return metadataKey(filter.key()) + computeFilter(">=", filter.comparisonValue());
    }

    private String mapLessThan(IsLessThan filter) {
        return metadataKey(filter.key()) + computeFilter("<", filter.comparisonValue());
    }

    private String mapLessThanOrEqual(IsLessThanOrEqualTo filter) {
        return metadataKey(filter.key()) + computeFilter("<=", filter.comparisonValue());
    }

    private String mapIn(IsIn filter) {
        ensureNotEmpty(filter.comparisonValues());
        String column = "m" + i + "." + valueColumn(filter.comparisonValues());
        String inStatement = formattedComparisonValues(filter.comparisonValues());

        return metadataKey(filter.key()) + column + " IN (" + inStatement + ")";
    }

    private String mapNotIn(IsNotIn filter) {
        ensureNotEmpty(filter.comparisonValues());
        String m = "m" + i + ".";
        String column = m + valueColumn(filter.comparisonValues());
        String inStatement = formattedComparisonValues(filter.comparisonValues());

        return "(" + column + " NOT IN (" + inStatement + ")" + metadataKeyLast(filter.key()) + ") " + "OR ("
                + column + " IN (" + inStatement + ") and " + m + "name!='" + escape(filter.key()) + "')"
                + addMetadataNullCheck();
    }

    private void ensureNotEmpty(Collection<?> comparisonValues) {
        if (comparisonValues.isEmpty()) {
            throw new UnsupportedOperationException("Infinispan metadata filter IN must contain values");
        }
    }

    private String valueColumn(Collection<?> comparisonValues) {
        if (comparisonValues.stream().anyMatch(v -> v instanceof Float || v instanceof Double)) {
            return "value_float";
        }
        if (comparisonValues.stream().allMatch(v -> v instanceof Integer || v instanceof Long)) {
            return "value_int";
        }
        return "value";
    }

    private String computeFilter(String operator, Object value) {
        String m = "m" + i + ".";
        String filterQuery = m + "value " + operator + " '" + escape(String.valueOf(value)) + "'";
        if (value instanceof Integer || value instanceof Long) {
            Long longValue = getLongValue(value);
            filterQuery = m + "value_int " + operator + " " + longValue;
        } else if (value instanceof Float || value instanceof Double) {
            Double doubleValue = getDoubleValue(value);
            filterQuery = m + "value_float " + operator + " " + doubleValue;
        }
        return filterQuery;
    }

    private String addMetadataNullCheck() {
        return " OR (i.metadata is null) ";
    }

    private Double getDoubleValue(Object value) {
        Double doubleValue = value instanceof Float ? ((Float) value).doubleValue() : (Double) value;
        return doubleValue;
    }

    private Long getLongValue(Object value) {
        Long longValue = value instanceof Integer ? ((Integer) value).longValue() : (Long) value;
        return longValue;
    }

    private String mapAnd(And filter) {
        return "((" + map(filter.left()).query + ") AND (" + map(filter.right()).query + "))";
    }

    private String mapNot(Not filter) {
        return "(NOT (" + map(filter.expression()).query + "))";
    }

    private String mapOr(Or filter) {
        return "((" + map(filter.left()).query + ") OR (" + map(filter.right()).query + "))";
    }

    private String metadataKeyLast(String key) {
        return " and m" + i + ".name='" + escape(key) + "'";
    }

    private String metadataKey(String key) {
        return "m" + i + ".name='" + escape(key) + "' and ";
    }

    private String formattedComparisonValues(Collection<?> comparisonValues) {
        boolean hasNumeric = comparisonValues.stream().anyMatch(v -> v instanceof Number);
        boolean hasNonNumeric = comparisonValues.stream().anyMatch(v -> !(v instanceof Number));
        if (hasNumeric && hasNonNumeric) {
            throw new IllegalArgumentException(
                    "Infinispan metadata filter IN/NOT IN cannot mix numeric and non-numeric values");
        }
        boolean asFloat = "value_float".equals(valueColumn(comparisonValues));
        return comparisonValues.stream()
                .map(s -> formattedComparisonValue(s, asFloat))
                .collect(Collectors.joining(", "));
    }

    private String formattedComparisonValue(Object value, boolean asFloat) {
        if (!(value instanceof Number)) {
            return "'" + escape(String.valueOf(value)) + "'";
        }
        if (asFloat && (value instanceof Integer || value instanceof Long)) {
            return String.valueOf(((Number) value).doubleValue());
        }
        return value.toString();
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("'", "''");
    }
}
