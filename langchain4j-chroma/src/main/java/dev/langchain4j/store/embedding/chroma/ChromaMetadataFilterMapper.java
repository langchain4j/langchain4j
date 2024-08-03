package dev.langchain4j.store.embedding.chroma;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonMap;

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
import dev.langchain4j.store.embedding.filter.logical.Or;
import java.util.Map;

class ChromaMetadataFilterMapper {

    ChromaMetadataFilterMapper() {
        // no instance possible
    }

    // TODO add the tests
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
}
