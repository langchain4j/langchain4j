package dev.langchain4j.store.embedding.pinecone;


import com.google.protobuf.ListValue;
import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.comparison.*;
import dev.langchain4j.store.embedding.filter.logical.And;
import dev.langchain4j.store.embedding.filter.logical.Not;
import dev.langchain4j.store.embedding.filter.logical.Or;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;

/**
 * <a href="https://docs.pinecone.io/guides/data/filter-with-metadata#more-example-filter-expressions">Pinecone Filter doc</a>
 */
public class PineconeMetadataFilterMapper {

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
        } else if (filter instanceof Not) {
            return mapNot((Not) filter);
        } else if (filter instanceof Or) {
            return mapOr((Or) filter);
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
        Value.Builder valueBuilder = Value.newBuilder();
        // for In and NotIn
        if (comparisonValue instanceof Collection) {
            valueBuilder.setListValue(
                    ListValue.newBuilder()
                            .addAllValues(
                                    ((Collection<?>) comparisonValue).stream()
                                            .map(Object::toString)
                                            .map(Value.newBuilder()::setStringValue)
                                            .map(Value.Builder::build)
                                            .collect(toList())
                            ).build()
            );
        } else {
            valueBuilder.setStringValue(comparisonValue.toString());
        }
        return Struct.newBuilder().putFields(
                key,
                Value.newBuilder().setStructValue(
                        Struct.newBuilder().putFields(
                                ATOMIC_PREDICT_MAP.get(clazz),
                                valueBuilder.build()
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
