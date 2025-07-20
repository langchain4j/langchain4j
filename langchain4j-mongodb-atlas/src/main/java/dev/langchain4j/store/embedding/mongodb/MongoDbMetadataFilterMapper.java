package dev.langchain4j.store.embedding.mongodb;

import com.mongodb.client.model.Filters;
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
import org.bson.conversions.Bson;

class MongoDbMetadataFilterMapper {

    public static Bson map(Filter filter) {
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
            throw new UnsupportedOperationException("Unsupported filter type: " + filter.getClass().getName());
        }
    }

    private static String getFieldName(String key) {
        return "metadata." + key;
    }

    private static Bson mapEqual(IsEqualTo filter) {
        return Filters.eq(getFieldName(filter.key()), filter.comparisonValue() );
    }

    private static Bson mapNotEqual(IsNotEqualTo filter) {
        return Filters.ne(getFieldName(filter.key()), filter.comparisonValue());
    }

    private static Bson mapGreaterThan(IsGreaterThan filter) {
        return Filters.gt(getFieldName(filter.key()), filter.comparisonValue());
    }

    private static Bson mapGreaterThanOrEqual(IsGreaterThanOrEqualTo filter) {
        return Filters.gte(getFieldName(filter.key()), filter.comparisonValue());
    }

    private static Bson mapLessThan(IsLessThan filter) {
        return Filters.lt(getFieldName(filter.key()), filter.comparisonValue());
    }

    private static Bson mapLessThanOrEqual(IsLessThanOrEqualTo filter) {
        return Filters.lte(getFieldName(filter.key()), filter.comparisonValue());
    }

    private static Bson mapIn(IsIn filter) {
        return Filters.in(getFieldName(filter.key()), filter.comparisonValues());
    }

    private static Bson mapNotIn(IsNotIn filter) {
        return Filters.nin(getFieldName(filter.key()), filter.comparisonValues());
    }

    private static Bson mapAnd(And filter) {
        return Filters.and(map(filter.left()), map(filter.right()));
    }

    private static Bson mapOr(Or filter) {
        return Filters.or(map(filter.left()), map(filter.right()));
    }

    private static Bson mapNot(Not filter) {
        return Filters.not(map(filter.expression()));
    }
}