package dev.langchain4j.store.embedding.astradb;

import dev.langchain4j.store.embedding.filter.comparison.IsEqualTo;
import dev.langchain4j.store.embedding.filter.comparison.IsNotEqualTo;
import dev.langchain4j.store.embedding.filter.comparison.IsGreaterThan;
import dev.langchain4j.store.embedding.filter.comparison.IsGreaterThanOrEqualTo;
import dev.langchain4j.store.embedding.filter.comparison.IsLessThan;
import dev.langchain4j.store.embedding.filter.comparison.IsLessThanOrEqualTo;
import dev.langchain4j.store.embedding.filter.comparison.IsIn;
import dev.langchain4j.store.embedding.filter.comparison.IsNotIn;
import dev.langchain4j.store.embedding.filter.logical.And;
import dev.langchain4j.store.embedding.filter.logical.Not;
import dev.langchain4j.store.embedding.filter.logical.Or;
import io.stargate.sdk.data.domain.query.Filter;
import io.stargate.sdk.utils.JsonUtils;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Translates LangChain4j {@link dev.langchain4j.store.embedding.filter.Filter} objects
 * into Stargate SDK {@link Filter} objects for metadata filtering in AstraDB.
 */
class AstraDbFilterMapper {

    static Filter map(dev.langchain4j.store.embedding.filter.Filter filter) {
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
        } else if (filter instanceof Or) {
            return mapOr((Or) filter);
        } else if (filter instanceof Not) {
            return mapNot((Not) filter);
        } else {
            throw new UnsupportedOperationException(
                "Unsupported filter type: " + filter.getClass().getName());
        }
    }

    private static Filter mapEqual(IsEqualTo f) {
        return new Filter().where(f.key()).isEqualsTo(f.comparisonValue());
    }

    private static Filter mapNotEqual(IsNotEqualTo f) {
        return new Filter().where(f.key()).isNotEqualsTo(f.comparisonValue());
    }

    private static Filter mapGreaterThan(IsGreaterThan f) {
        return new Filter().where(f.key()).isGreaterThan(f.comparisonValue());
    }

    private static Filter mapGreaterThanOrEqual(IsGreaterThanOrEqualTo f) {
        return new Filter().where(f.key()).isGreaterOrEqualsThan(f.comparisonValue());
    }

    private static Filter mapLessThan(IsLessThan f) {
        return new Filter().where(f.key()).isLessThan(f.comparisonValue());
    }

    private static Filter mapLessThanOrEqual(IsLessThanOrEqualTo f) {
        return new Filter().where(f.key()).isLessOrEqualsThan(f.comparisonValue());
    }

    private static Filter mapIn(IsIn f) {
        Map<String, Object> inner = new HashMap<>();
        inner.put("$in", f.comparisonValues().toArray());
        Map<String, Object> outer = new HashMap<>();
        outer.put(f.key(), inner);
        return new Filter(JsonUtils.marshallForDataApi(outer));
    }

    private static Filter mapNotIn(IsNotIn f) {
        Map<String, Object> inner = new HashMap<>();
        inner.put("$nin", f.comparisonValues().toArray());
        Map<String, Object> outer = new HashMap<>();
        outer.put(f.key(), inner);
        return new Filter(JsonUtils.marshallForDataApi(outer));
    }

    private static Filter mapAnd(And f) {
        Map<String, Object> map = new HashMap<>();
        map.put("$and", Arrays.asList(
            map(f.left()).getFilter(),
            map(f.right()).getFilter()
        ));
        return new Filter(JsonUtils.marshallForDataApi(map));
    }

    private static Filter mapOr(Or f) {
        Map<String, Object> map = new HashMap<>();
        map.put("$or", Arrays.asList(
            map(f.left()).getFilter(),
            map(f.right()).getFilter()
        ));
        return new Filter(JsonUtils.marshallForDataApi(map));
    }

    private static Filter mapNot(Not f) {
        Map<String, Object> map = new HashMap<>();
        map.put("$not", map(f.expression()).getFilter());
        return new Filter(JsonUtils.marshallForDataApi(map));
    }
}
