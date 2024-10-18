package dev.langchain4j.store.embedding.pinecone;


import com.google.protobuf.ListValue;
import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.comparison.*;
import dev.langchain4j.store.embedding.filter.logical.And;
import dev.langchain4j.store.embedding.filter.logical.Not;
import dev.langchain4j.store.embedding.filter.logical.Or;

import java.util.AbstractMap;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;

/**
 * <a href="https://docs.pinecone.io/guides/data/filter-with-metadata#querying-an-index-with-metadata-filters">Pinecone Filter doc</a>
 */
class PineconeMetadataFilterMapper {

    private static final Map<Class<? extends Filter>, String> ATOMIC_PREDICT_MAP = Stream.of(
                    new AbstractMap.SimpleEntry<>(IsEqualTo.class, "$eq"),
                    new AbstractMap.SimpleEntry<>(IsNotEqualTo.class, "$ne"),
                    new AbstractMap.SimpleEntry<>(IsGreaterThan.class, "$gt"),
                    new AbstractMap.SimpleEntry<>(IsGreaterThanOrEqualTo.class, "$gte"),
                    new AbstractMap.SimpleEntry<>(IsLessThan.class, "$lt"),
                    new AbstractMap.SimpleEntry<>(IsLessThanOrEqualTo.class, "$lte"),
                    new AbstractMap.SimpleEntry<>(IsIn.class, "$in"),
                    new AbstractMap.SimpleEntry<>(IsNotIn.class, "$nin"))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

    private static final Map<Class<? extends Filter>, String> COMBINE_PREDICT_MAP = Stream.of(
                    new AbstractMap.SimpleEntry<>(And.class, "$and"),
                    new AbstractMap.SimpleEntry<>(Or.class, "$or"))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));


    static Struct map(Filter filter) {
        if (filter instanceof IsEqualTo to) {
            return mapEqual(to);
        } else if (filter instanceof IsNotEqualTo to) {
            return mapNotEqual(to);
        } else if (filter instanceof IsGreaterThan than) {
            return mapGreaterThan(than);
        } else if (filter instanceof IsGreaterThanOrEqualTo to) {
            return mapGreaterThanOrEqual(to);
        } else if (filter instanceof IsLessThan than) {
            return mapLessThan(than);
        } else if (filter instanceof IsLessThanOrEqualTo to) {
            return mapLessThanOrEqual(to);
        } else if (filter instanceof IsIn in) {
            return mapIn(in);
        } else if (filter instanceof IsNotIn in) {
            return mapNotIn(in);
        } else if (filter instanceof And and) {
            return mapAnd(and);
        } else if (filter instanceof Not not) {
            return mapNot(not);
        } else if (filter instanceof Or or) {
            return mapOr(or);
        } else {
            throw new UnsupportedOperationException("Unsupported filter type: " + filter.getClass().getName());
        }
    }

    private static Struct mapEqual(IsEqualTo isEqualTo) {
        return mapAtomicPredict(IsEqualTo.class, isEqualTo.key(), isEqualTo.comparisonValue());
    }

    private static Struct mapNotEqual(IsNotEqualTo isNotEqualTo) {
        return mapAtomicPredict(IsNotEqualTo.class, isNotEqualTo.key(), isNotEqualTo.comparisonValue());
    }


    private static Struct mapGreaterThan(IsGreaterThan isGreaterThan) {
        return mapAtomicPredict(IsGreaterThan.class, isGreaterThan.key(), isGreaterThan.comparisonValue());
    }

    private static Struct mapGreaterThanOrEqual(IsGreaterThanOrEqualTo isGreaterThanOrEqualTo) {
        return mapAtomicPredict(IsGreaterThanOrEqualTo.class, isGreaterThanOrEqualTo.key(), isGreaterThanOrEqualTo.comparisonValue());
    }

    private static Struct mapLessThan(IsLessThan isLessThan) {
        return mapAtomicPredict(IsLessThan.class, isLessThan.key(), isLessThan.comparisonValue());
    }

    private static Struct mapLessThanOrEqual(IsLessThanOrEqualTo isLessThanOrEqualTo) {
        return mapAtomicPredict(IsLessThanOrEqualTo.class, isLessThanOrEqualTo.key(), isLessThanOrEqualTo.comparisonValue());
    }

    public static Struct mapIn(IsIn isIn) {
        return mapAtomicPredict(IsIn.class, isIn.key(), isIn.comparisonValues());
    }

    public static Struct mapNotIn(IsNotIn isNotIn) {
        return mapAtomicPredict(IsNotIn.class, isNotIn.key(), isNotIn.comparisonValues());
    }

    private static Struct mapAnd(And and) {
        return mapCombinePredict(And.class, and.left(), and.right());
    }

    private static Struct mapOr(Or or) {
        return mapCombinePredict(Or.class, or.left(), or.right());
    }

    private static Struct mapAtomicPredict(Class<? extends Filter> clazz, String key, Object comparisonValue) {
        // for In and NotIn
        return Struct.newBuilder().putFields(
                key,
                Value.newBuilder().setStructValue(
                        Struct.newBuilder().putFields(
                                ATOMIC_PREDICT_MAP.get(clazz),
                                getValueBuilder(comparisonValue).build()
                        ).build()
                ).build()
        ).build();
    }

    private static Struct mapCombinePredict(Class<? extends Filter> clazz, Filter left, Filter right) {
        return Struct.newBuilder().putFields(
                COMBINE_PREDICT_MAP.get(clazz),
                Value.newBuilder().setListValue(
                        ListValue.newBuilder()
                                .addValues(Value.newBuilder().setStructValue(map(left)).build())
                                .addValues(Value.newBuilder().setStructValue(map(right)).build())
                                .build()
                ).build()
        ).build();
    }

    /**
     * pinecone does not support not operation, so we need to convert it to other operations
     */
    private static Struct mapNot(Not not) {
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

    private static Value.Builder getValueBuilder(Object value) {
        if (value instanceof Number number) {
            return Value.newBuilder().setNumberValue(number.doubleValue());
        } else if (value instanceof String || value instanceof UUID) {
            return Value.newBuilder().setStringValue(value.toString());
        } else if (value instanceof Boolean boolean1) {
            return Value.newBuilder().setBoolValue(boolean1);
        } else if (value instanceof Collection<?> collection) {
            return Value.newBuilder().setListValue(
                    ListValue.newBuilder().addAllValues(
                            collection.stream()
                                    .map(PineconeMetadataFilterMapper::getValueBuilder)
                                    .map(Value.Builder::build)
                                    .collect(toList())
                    ).build()
            );
        } else {
            throw new UnsupportedOperationException("Unsupported value type: " + value.getClass().getName());
        }
    }

}
