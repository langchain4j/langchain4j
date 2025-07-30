package dev.langchain4j.store.embedding.infinispan;

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
import java.util.Optional;
import java.util.stream.Collectors;

class InfinispanMetadataFilterMapper {

    static class FilterResult {
        String join;
        String query;

        public FilterResult(String query, int metadataIndex) {
            this.query = query;
            StringBuilder sb = new StringBuilder();
            for (int j = 0; j <= metadataIndex; j++) {
                sb.append(" join i.metadata m").append(j);
            }
            this.join = sb.toString();
        }
    }

    FilterResult map(Filter filter) {
        return mapWithIndex(filter, -1);
    }

    private FilterResult mapWithIndex(Filter filter, int metadataIndex) {
        if (filter == null) {
            return null;
        }

        int currentIndex = metadataIndex + 1;
        String filterQuery = "";

        if (filter instanceof IsEqualTo) {
            filterQuery = mapEqual((IsEqualTo) filter, currentIndex);
        } else if (filter instanceof IsNotEqualTo) {
            filterQuery = mapNotEqual((IsNotEqualTo) filter, currentIndex);
        } else if (filter instanceof IsGreaterThan) {
            filterQuery = mapGreaterThan((IsGreaterThan) filter, currentIndex);
        } else if (filter instanceof IsGreaterThanOrEqualTo) {
            filterQuery = mapGreaterThanOrEqual((IsGreaterThanOrEqualTo) filter, currentIndex);
        } else if (filter instanceof IsLessThan) {
            filterQuery = mapLessThan((IsLessThan) filter, currentIndex);
        } else if (filter instanceof IsLessThanOrEqualTo) {
            filterQuery = mapLessThanOrEqual((IsLessThanOrEqualTo) filter, currentIndex);
        } else if (filter instanceof IsIn) {
            filterQuery = mapIn((IsIn) filter, currentIndex);
        } else if (filter instanceof IsNotIn) {
            filterQuery = mapNotIn((IsNotIn) filter, currentIndex);
        } else if (filter instanceof And) {
            filterQuery = mapAnd((And) filter, currentIndex);
        } else if (filter instanceof Not) {
            filterQuery = mapNot((Not) filter, currentIndex);
        } else if (filter instanceof Or) {
            filterQuery = mapOr((Or) filter, currentIndex);
        } else {
            throw new UnsupportedOperationException(
                    "Unsupported filter type: " + filter.getClass().getName());
        }

        return new FilterResult(filterQuery, currentIndex);
    }

    private String mapEqual(IsEqualTo filter, int index) {
        return metadataKey(filter.key(), index) + computeFilter("=", filter.comparisonValue(), index);
    }

    private String mapNotEqual(IsNotEqualTo filter, int index) {
        return computeFilter("!=", filter.comparisonValue(), index)
                + metadataKeyLast(filter.key(), index)
                + addMetadataNullCheck();
    }

    private String mapGreaterThan(IsGreaterThan filter, int index) {
        return metadataKey(filter.key(), index) + computeFilter(">", filter.comparisonValue(), index);
    }

    private String mapGreaterThanOrEqual(IsGreaterThanOrEqualTo filter, int index) {
        return metadataKey(filter.key(), index) + computeFilter(">=", filter.comparisonValue(), index);
    }

    private String mapLessThan(IsLessThan filter, int index) {
        return metadataKey(filter.key(), index) + computeFilter("<", filter.comparisonValue(), index);
    }

    private String mapLessThanOrEqual(IsLessThanOrEqualTo filter, int index) {
        return metadataKey(filter.key(), index) + computeFilter("<=", filter.comparisonValue(), index);
    }

    private String mapIn(IsIn filter, int index) {
        Optional<?> first = filter.comparisonValues().stream().findFirst();
        if (first.isEmpty()) {
            throw new UnsupportedOperationException("Infinispan metadata filter IN must contain values");
        }
        Object o = first.get();
        String inStatement = formattedComparisonValues(filter.comparisonValues(), o instanceof Number);
        String m = "m" + index + ".";
        String filterQuery = m + "value IN (" + inStatement + ")";
        if (o instanceof Integer || o instanceof Long) {
            filterQuery = m + "value_int IN (" + inStatement + ")";
        } else if (o instanceof Float || o instanceof Double) {
            filterQuery = m + "value_float IN (" + inStatement + ")";
        }

        return metadataKey(filter.key(), index) + filterQuery;
    }

    private String mapNotIn(IsNotIn filter, int index) {
        Optional<?> first = filter.comparisonValues().stream().findFirst();
        if (first.isEmpty()) {
            throw new UnsupportedOperationException("Infinispan metadata filter IN must contain values");
        }
        Object o = first.get();
        String inStatement = formattedComparisonValues(filter.comparisonValues(), o instanceof Number);
        String m = "m" + index + ".";
        String filterQuery = m + "value NOT IN (" + inStatement + ")";
        if (o instanceof Integer || o instanceof Long) {
            filterQuery = m + "value_int NOT IN (" + inStatement + ")";
        } else if (o instanceof Float || o instanceof Double) {
            filterQuery = m + "value_float NOT IN (" + inStatement + ")";
        }

        String inFilterQuery = m + "value IN (" + inStatement + ")";
        if (o instanceof Integer || o instanceof Long) {
            inFilterQuery = m + "value_int IN (" + inStatement + ")";
        } else if (o instanceof Float || o instanceof Double) {
            inFilterQuery = m + "value_float IN (" + inStatement + ")";
        }

        return "(" + filterQuery + metadataKeyLast(filter.key(), index) + ") " + "OR ("
                + inFilterQuery + " and " + m + "name!='" + filter.key() + "')"
                + addMetadataNullCheck();
    }

    private String computeFilter(String operator, Object value, int index) {
        String m = "m" + index + ".";
        String filterQuery = m + "value " + operator + " '" + value + "'";
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

    private String mapAnd(And filter, int index) {
        FilterResult leftResult = mapWithIndex(filter.left(), index);
        FilterResult rightResult = mapWithIndex(filter.right(), index);
        return "((" + leftResult.query + ") AND (" + rightResult.query + "))";
    }

    private String mapNot(Not filter, int index) {
        FilterResult expressionResult = mapWithIndex(filter.expression(), index);
        return "(NOT (" + expressionResult.query + "))";
    }

    private String mapOr(Or filter, int index) {
        FilterResult leftResult = mapWithIndex(filter.left(), index);
        FilterResult rightResult = mapWithIndex(filter.right(), index);
        return "((" + leftResult.query + ") OR (" + rightResult.query + "))";
    }

    private String metadataKeyLast(String key, int index) {
        return " and m" + index + ".name='" + key + "'";
    }

    private String metadataKey(String key, int index) {
        return "m" + index + ".name='" + key + "' and ";
    }

    private String formattedComparisonValues(Collection<?> comparisonValues, boolean isNumeric) {
        String inStatement = comparisonValues.stream()
                .map(s -> isNumeric ? s.toString() : "'" + s + "'")
                .collect(Collectors.joining(", "));
        return inStatement;
    }
}
