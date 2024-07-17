package dev.langchain4j.store.embedding.couchbase;

import com.couchbase.client.core.deps.com.fasterxml.jackson.databind.ObjectMapper;
import com.couchbase.client.java.search.SearchQuery;
import com.couchbase.client.java.search.queries.BooleanQuery;
import com.couchbase.client.java.search.queries.MatchQuery;
import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.comparison.*;
import dev.langchain4j.store.embedding.filter.logical.And;
import dev.langchain4j.store.embedding.filter.logical.Not;
import dev.langchain4j.store.embedding.filter.logical.Or;
import reactor.util.annotation.NonNull;

import java.util.Collection;

class CouchbaseSearchMetadataFilterMapper {
    private static final ObjectMapper mapper = new ObjectMapper();

    static SearchQuery map(Filter filter) {
        if (filter == null) {
            return null;
        } else if (filter instanceof IsEqualTo) {
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

    private static SearchQuery mapEqual(IsEqualTo isEqualTo) {
        return MatchQuery.match(String.valueOf(isEqualTo.comparisonValue()))
                .field(formatKey(isEqualTo.key(), isEqualTo.comparisonValue()));
    }

    private static SearchQuery mapNotEqual(IsNotEqualTo isNotEqualTo) {
        return BooleanQuery.booleans().mustNot(
                MatchQuery.match(String.valueOf(isNotEqualTo.comparisonValue()))
                        .field(formatKey(isNotEqualTo.key(), isNotEqualTo.comparisonValue()))
        );
    }

    private static SearchQuery mapGreaterThan(IsGreaterThan isGreaterThan) {
        return SearchQuery.numericRange()
                .min(Double.valueOf(String.valueOf(isGreaterThan.comparisonValue())), false)
                .field(formatKey(isGreaterThan.key(), isGreaterThan.comparisonValue()));
    }

    private static SearchQuery mapGreaterThanOrEqual(IsGreaterThanOrEqualTo isGreaterThanOrEqualTo) {
        return SearchQuery.numericRange()
                .min(Double.valueOf(String.valueOf(isGreaterThanOrEqualTo.comparisonValue())), true)
                .field(formatKey(isGreaterThanOrEqualTo.key(), isGreaterThanOrEqualTo.comparisonValue()));
    }

    private static SearchQuery mapLessThan(IsLessThan isLessThan) {
        return SearchQuery.numericRange()
                .max(Double.valueOf(String.valueOf(isLessThan.comparisonValue())), false)
                .field(formatKey(isLessThan.key(), isLessThan.comparisonValue()));
    }

    private static SearchQuery mapLessThanOrEqual(IsLessThanOrEqualTo isLessThanOrEqualTo) {
        return SearchQuery.numericRange()
                .max(Double.valueOf(String.valueOf(isLessThanOrEqualTo.comparisonValue())), true)
                .field(formatKey(isLessThanOrEqualTo.key(), isLessThanOrEqualTo.comparisonValue()));
    }

    public static SearchQuery mapIn(IsIn isIn) {
        final String key = formatKey(isIn.key(), null);
        return SearchQuery.disjuncts(isIn.comparisonValues().stream()
                .map(v -> SearchQuery.match(v.toString()).field(key))
                .toArray(SearchQuery[]::new)
        );
    }

    public static SearchQuery mapNotIn(IsNotIn isNotIn) {
        final String key = formatKey(isNotIn.key(), null);
        final int cvSize = isNotIn.comparisonValues().size();
        return SearchQuery.disjuncts(isNotIn.comparisonValues().stream()
                .map(v -> SearchQuery.match(v.toString()).field(key))
                .toArray(SearchQuery[]::new)).min(cvSize);
    }

    @NonNull
    private static SearchQuery mapAnd(And and) {
        return BooleanQuery.booleans().must(map(and.left()), map(and.right()));
    }

    @NonNull
    private static SearchQuery mapNot(Not not) {
        return BooleanQuery.booleans().mustNot(map(not.expression()));
    }

    private static SearchQuery mapOr(Or or) {
        return BooleanQuery.booleans().should(
                map(or.left()),
                map(or.right())
        );
    }

    private static String formatKey(String key, Object comparisonValue) {
        if (comparisonValue instanceof String) {
            return String.format("`metadata`.`%s`.`keyword`", key);
        } else {
            return String.format("`metadata`.`%s`", key);
        }
    }

    private static String formatKey(String key, Collection<?> comparisonValues) {
        if (comparisonValues.iterator().next() instanceof String) {
            return String.format("`metadata`.`%s`.`keyword`", key);
        } else {
            return String.format("`metadata`.`%s`", key);
        }
    }
}

