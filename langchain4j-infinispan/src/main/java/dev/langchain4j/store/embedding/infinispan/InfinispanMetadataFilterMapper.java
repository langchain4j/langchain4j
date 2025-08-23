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
import java.util.Optional;
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
            i++;
        } else if (filter instanceof IsGreaterThanOrEqualTo) {
            i++;
            filterQuery = mapGreaterThanOrEqual((IsGreaterThanOrEqualTo) filter);
            i++;
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
        Optional<?> first = filter.comparisonValues().stream().findFirst();
        if (first.isEmpty()) {
            throw new UnsupportedOperationException("Infinispan metadata filter IN must contain values");
        }
        Object o = first.get();
        String inStatement = formattedComparisonValues(filter.comparisonValues(), o instanceof Number);
        String m = "m" + i + ".";
        String filterQuery = m + "value IN (" + inStatement + ")";
        if (o instanceof Integer || o instanceof Long) {
            filterQuery = m + "value_int IN (" + inStatement + ")";
        } else if (o instanceof Float || o instanceof Double) {
            filterQuery = m + "value_float IN (" + inStatement + ")";
        }

        return metadataKey(filter.key()) + filterQuery;
    }

    private String mapNotIn(IsNotIn filter) {
        Optional<?> first = filter.comparisonValues().stream().findFirst();
        if (first.isEmpty()) {
            throw new UnsupportedOperationException("Infinispan metadata filter IN must contain values");
        }
        Object o = first.get();
        String inStatement = formattedComparisonValues(filter.comparisonValues(), o instanceof Number);
        String m = "m" + i + ".";
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

        return "(" + filterQuery + metadataKeyLast(filter.key()) + ") " + "OR ("
                + inFilterQuery + " and " + m + "name!='" + filter.key() + "')"
                + addMetadataNullCheck();
    }

    private String computeFilter(String operator, Object value) {
        String m = "m" + i + ".";
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
        return " and m" + i + ".name='" + key + "'";
    }

    private String metadataKey(String key) {
        return "m" + i + ".name='" + key + "' and ";
    }

    private String formattedComparisonValues(Collection<?> comparisonValues, boolean isNumeric) {
        String inStatement = comparisonValues.stream()
                .map(s -> isNumeric ? s.toString() : "'" + s + "'")
                .collect(Collectors.joining(", "));
        return inStatement;
    }
}
