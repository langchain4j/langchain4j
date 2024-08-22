package dev.langchain4j.store.embedding.chroma;

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

import java.util.Map;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonMap;

class ChromaMetadataFilterMapper {

    ChromaMetadataFilterMapper() {
        // no instance possible
    }

    static Map<String, Object> map(Filter filter) {
        if (filter == null) {
            return null;
        } else if (filter instanceof IsEqualTo) {
            return mapEqual((IsEqualTo) filter);
        } else if (filter instanceof IsNotEqualTo) {
            return mapNotEqual((IsNotEqualTo) filter);
        } else if (filter instanceof IsGreaterThan) {
            return mapIsGreaterThan((IsGreaterThan) filter);
        } else if (filter instanceof IsGreaterThanOrEqualTo) {
            return mapIsGreaterThanOrEqualTo((IsGreaterThanOrEqualTo) filter);
        } else if (filter instanceof IsLessThan) {
            return mapIsLessThan((IsLessThan) filter);
        } else if (filter instanceof IsLessThanOrEqualTo) {
            return mapIsLessThanOrEqualTo((IsLessThanOrEqualTo) filter);
        } else if (filter instanceof IsIn) {
            return mapIsIn((IsIn) filter);
        } else if (filter instanceof IsNotIn) {
            return mapIsNotIn((IsNotIn) filter);
        } else if (filter instanceof And) {
            return mapAnd((And) filter);
        } else if (filter instanceof Or) {
            return mapOr((Or) filter);
        } else if (filter instanceof Not) {
            return mapNot((Not) filter);
        } else {
            throw new UnsupportedOperationException("Unsupported filter type: " + filter.getClass().getName());
        }
    }

    private static Map<String, Object> mapEqual(IsEqualTo filter) {
        return singletonMap(filter.key(), filter.comparisonValue());
    }

    private static Map<String, Object> mapNotEqual(IsNotEqualTo filter) {
        return singletonMap(filter.key(), singletonMap("$ne", filter.comparisonValue()));
    }

    private static Map<String, Object> mapIsGreaterThan(IsGreaterThan filter) {
        return singletonMap(filter.key(), singletonMap("$gt", filter.comparisonValue()));
    }

    private static Map<String, Object> mapIsGreaterThanOrEqualTo(IsGreaterThanOrEqualTo filter) {
        return singletonMap(filter.key(), singletonMap("$gte", filter.comparisonValue()));
    }

    private static Map<String, Object> mapIsLessThan(IsLessThan filter) {
        return singletonMap(filter.key(), singletonMap("$lt", filter.comparisonValue()));
    }

    private static Map<String, Object> mapIsLessThanOrEqualTo(IsLessThanOrEqualTo filter) {
        return singletonMap(filter.key(), singletonMap("$lte", filter.comparisonValue()));
    }

    private static Map<String, Object> mapIsIn(IsIn filter) {
        return singletonMap(filter.key(), singletonMap("$in", filter.comparisonValues()));
    }

    private static Map<String, Object> mapIsNotIn(IsNotIn filter) {
        return singletonMap(filter.key(), singletonMap("$nin", filter.comparisonValues()));
    }

    private static Map<String, Object> mapAnd(And and) {
        return singletonMap("$and", asList(map(and.left()), map(and.right())));
    }

    private static Map<String, Object> mapOr(Or or) {
        return singletonMap("$or", asList(map(or.left()), map(or.right())));
    }

    /**
     * Chroma does not support "not" operation, so we need to convert it to other operations
     */
    private static Map<String, Object> mapNot(Not not) {
        Filter expression = not.expression();
        if (expression instanceof IsEqualTo) {
            expression = new IsNotEqualTo(((IsEqualTo) expression).key(), ((IsEqualTo) expression).comparisonValue());
        } else if (expression instanceof IsNotEqualTo) {
            expression = new IsEqualTo(((IsNotEqualTo) expression).key(), ((IsNotEqualTo) expression).comparisonValue());
        } else if (expression instanceof IsGreaterThan) {
            expression = new IsLessThanOrEqualTo(((IsGreaterThan) expression).key(), ((IsGreaterThan) expression).comparisonValue());
        } else if (expression instanceof IsGreaterThanOrEqualTo) {
            expression = new IsLessThan(((IsGreaterThanOrEqualTo) expression).key(), ((IsGreaterThanOrEqualTo) expression).comparisonValue());
        } else if (expression instanceof IsLessThan) {
            expression = new IsGreaterThanOrEqualTo(((IsLessThan) expression).key(), ((IsLessThan) expression).comparisonValue());
        } else if (expression instanceof IsLessThanOrEqualTo) {
            expression = new IsGreaterThan(((IsLessThanOrEqualTo) expression).key(), ((IsLessThanOrEqualTo) expression).comparisonValue());
        } else if (expression instanceof IsIn) {
            expression = new IsNotIn(((IsIn) expression).key(), ((IsIn) expression).comparisonValues());
        } else if (expression instanceof IsNotIn) {
            expression = new IsIn(((IsNotIn) expression).key(), ((IsNotIn) expression).comparisonValues());
        } else if (expression instanceof And) {
            expression = new Or(Filter.not(((And) expression).left()), Filter.not(((And) expression).right()));
        } else if (expression instanceof Or) {
            expression = new And(Filter.not(((Or) expression).left()), Filter.not(((Or) expression).right()));
        } else {
            throw new UnsupportedOperationException("Unsupported filter type: " + expression.getClass().getName());
        }
        return map(expression);
    }
}
