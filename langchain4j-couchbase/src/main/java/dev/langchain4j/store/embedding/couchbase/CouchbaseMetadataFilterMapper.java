package dev.langchain4j.store.embedding.couchbase;

import com.couchbase.client.core.deps.com.fasterxml.jackson.core.JsonProcessingException;
import com.couchbase.client.core.deps.com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.comparison.*;
import dev.langchain4j.store.embedding.filter.logical.And;
import dev.langchain4j.store.embedding.filter.logical.Not;
import dev.langchain4j.store.embedding.filter.logical.Or;

import java.util.Collection;


class CouchbaseMetadataFilterMapper {
    private static final ObjectMapper mapper = new ObjectMapper();

    static String map(Filter filter) {
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

    private static String mapEqual(IsEqualTo isEqualTo) {
        try {
            final String key = formatKey(isEqualTo.key(), isEqualTo.comparisonValue());
            return String.format("%s = %s", key, mapper.writeValueAsString(isEqualTo.comparisonValue()));
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    private static String mapNotEqual(IsNotEqualTo isNotEqualTo) {
        try {
            final String key = formatKey(isNotEqualTo.key(), isNotEqualTo.comparisonValue());
            return String.format("%s != %s", key, mapper.writeValueAsString(isNotEqualTo.comparisonValue()));
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    private static String mapGreaterThan(IsGreaterThan isGreaterThan) {
        try {
            final String key = formatKey(isGreaterThan.key(), isGreaterThan.comparisonValue());
            return String.format("%s > %s", key, mapper.writeValueAsString(isGreaterThan.comparisonValue()));
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    private static String mapGreaterThanOrEqual(IsGreaterThanOrEqualTo isGreaterThanOrEqualTo) {
        try {
            final String key = formatKey(isGreaterThanOrEqualTo.key(), isGreaterThanOrEqualTo.comparisonValue());
            return String.format("%s >= %s", key, mapper.writeValueAsString(isGreaterThanOrEqualTo.comparisonValue()));
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    private static String mapLessThan(IsLessThan isLessThan) {
        try {
            final String key = formatKey(isLessThan.key(), isLessThan.comparisonValue());
            return String.format("%s < %s", key, mapper.writeValueAsString(isLessThan.comparisonValue()));
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    private static String mapLessThanOrEqual(IsLessThanOrEqualTo isLessThanOrEqualTo) {
        try {
            final String key = formatKey(isLessThanOrEqualTo.key(), isLessThanOrEqualTo.comparisonValue());
            return String.format("%s <= %s", key, mapper.writeValueAsString(isLessThanOrEqualTo.comparisonValue()));
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    public static String mapIn(IsIn isIn) {
        try {
            final String key = formatKey(isIn.key(), null);
            return String.format("%s IN %s", key, mapper.writeValueAsString(isIn.comparisonValues()));
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    public static String mapNotIn(IsNotIn isNotIn) {
        try {
            final String key = formatKey(isNotIn.key(), null);
            return String.format("%s NOT IN %s", key, mapper.writeValueAsString(isNotIn.comparisonValues()));
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    private static String mapAnd(And and) {
        return String.format("%s AND %s", map(and.left()), map(and.right()));
    }

    private static String mapNot(Not not) {
        return String.format("NOT %s", map(not.expression()));
    }

    private static String mapOr(Or or) {
        return String.format("%s OR %s", map(or.left()), map(or.right()));
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

