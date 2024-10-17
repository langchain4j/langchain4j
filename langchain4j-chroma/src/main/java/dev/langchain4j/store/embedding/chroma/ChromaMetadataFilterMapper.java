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
        } else if (filter instanceof IsEqualTo to) {
            return mapEqual(to);
        } else if (filter instanceof IsNotEqualTo to) {
            return mapNotEqual(to);
        } else if (filter instanceof IsGreaterThan than) {
            return mapIsGreaterThan(than);
        } else if (filter instanceof IsGreaterThanOrEqualTo to) {
            return mapIsGreaterThanOrEqualTo(to);
        } else if (filter instanceof IsLessThan than) {
            return mapIsLessThan(than);
        } else if (filter instanceof IsLessThanOrEqualTo to) {
            return mapIsLessThanOrEqualTo(to);
        } else if (filter instanceof IsIn in) {
            return mapIsIn(in);
        } else if (filter instanceof IsNotIn in) {
            return mapIsNotIn(in);
        } else if (filter instanceof And and) {
            return mapAnd(and);
        } else if (filter instanceof Or or) {
            return mapOr(or);
        } else if (filter instanceof Not not) {
            return mapNot(not);
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
        if (expression instanceof IsEqualTo to) {
            expression = new IsNotEqualTo(to.key(), to.comparisonValue());
        } else if (expression instanceof IsNotEqualTo to) {
            expression = new IsEqualTo(to.key(), to.comparisonValue());
        } else if (expression instanceof IsGreaterThan than) {
            expression = new IsLessThanOrEqualTo(than.key(), than.comparisonValue());
        } else if (expression instanceof IsGreaterThanOrEqualTo to) {
            expression = new IsLessThan(to.key(), to.comparisonValue());
        } else if (expression instanceof IsLessThan than) {
            expression = new IsGreaterThanOrEqualTo(than.key(), than.comparisonValue());
        } else if (expression instanceof IsLessThanOrEqualTo to) {
            expression = new IsGreaterThan(to.key(), to.comparisonValue());
        } else if (expression instanceof IsIn in) {
            expression = new IsNotIn(in.key(), in.comparisonValues());
        } else if (expression instanceof IsNotIn in) {
            expression = new IsIn(in.key(), in.comparisonValues());
        } else if (expression instanceof And and) {
            expression = new Or(Filter.not(and.left()), Filter.not(and.right()));
        } else if (expression instanceof Or or) {
            expression = new And(Filter.not(or.left()), Filter.not(or.right()));
        } else {
            throw new UnsupportedOperationException("Unsupported filter type: " + expression.getClass().getName());
        }
        return map(expression);
    }
}
