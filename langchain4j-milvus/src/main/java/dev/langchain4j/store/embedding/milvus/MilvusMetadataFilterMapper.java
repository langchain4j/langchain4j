package dev.langchain4j.store.embedding.milvus;

import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.comparison.*;
import dev.langchain4j.store.embedding.filter.logical.And;
import dev.langchain4j.store.embedding.filter.logical.Not;
import dev.langchain4j.store.embedding.filter.logical.Or;

import java.util.Collection;
import java.util.List;

import static java.lang.String.format;
import static java.util.stream.Collectors.toList;

class MilvusMetadataFilterMapper {

    static String map(Filter filter) {
        if (filter instanceof Equal) {
            return mapEqual((Equal) filter);
        } else if (filter instanceof NotEqual) {
            return mapNotEqual((NotEqual) filter);
        } else if (filter instanceof GreaterThan) {
            return mapGreaterThan((GreaterThan) filter);
        } else if (filter instanceof GreaterThanOrEqual) {
            return mapGreaterThanOrEqual((GreaterThanOrEqual) filter);
        } else if (filter instanceof LessThan) {
            return mapLessThan((LessThan) filter);
        } else if (filter instanceof LessThanOrEqual) {
            return mapLessThanOrEqual((LessThanOrEqual) filter);
        } else if (filter instanceof In) {
            return mapIn((In) filter);
        } else if (filter instanceof NotIn) {
            return mapNotIn((NotIn) filter);
        } else if (filter instanceof And) {
            return mapAnd((And) filter);
        } else if (filter instanceof Not) {
            return mapNot((Not) filter);
        } else if (filter instanceof Or) {
            return mapOr((Or) filter);
        } else {
            throw new UnsupportedOperationException("Unsupported metadataFilter type: " + filter.getClass().getName());
        }
    }

    private static String mapEqual(Equal equal) {
        return format("%s == %s", formatKey(equal.key()), formatValue(equal.comparisonValue()));
    }

    private static String mapNotEqual(NotEqual notEqual) {
        return format("%s != %s", formatKey(notEqual.key()), formatValue(notEqual.comparisonValue()));
    }

    private static String mapGreaterThan(GreaterThan greaterThan) {
        return format("%s > %s", formatKey(greaterThan.key()), formatValue(greaterThan.comparisonValue()));
    }

    private static String mapGreaterThanOrEqual(GreaterThanOrEqual greaterThanOrEqual) {
        return format("%s >= %s", formatKey(greaterThanOrEqual.key()), formatValue(greaterThanOrEqual.comparisonValue()));
    }

    private static String mapLessThan(LessThan lessThan) {
        return format("%s < %s", formatKey(lessThan.key()), formatValue(lessThan.comparisonValue()));
    }

    private static String mapLessThanOrEqual(LessThanOrEqual lessThanOrEqual) {
        return format("%s <= %s", formatKey(lessThanOrEqual.key()), formatValue(lessThanOrEqual.comparisonValue()));
    }

    public static String mapIn(In in) {
        return format("%s in %s", formatKey(in.key()), formatValues(in.comparisonValues()));
    }

    public static String mapNotIn(NotIn notIn) {
        return format("%s not in %s", formatKey(notIn.key()), formatValues(notIn.comparisonValues()));
    }

    private static String mapAnd(And and) {
        return format("%s and %s", map(and.left()), map(and.right()));
    }

    private static String mapNot(Not not) {
        return format("not(%s)", map(not.expression()));
    }

    private static String mapOr(Or or) {
        return format("(%s or %s)", map(or.left()), map(or.right()));
    }

    private static String formatKey(String key) {
        return "metadata[\"" + key + "\"]";
    }

    private static String formatValue(Object value) {
        if (value instanceof String) {
            return "\"" + value + "\"";
        } else {
            return value.toString();
        }
    }

    private static List<String> formatValues(Collection<?> values) {
        return values.stream().map(value -> {
            if (value instanceof String) {
                return "\"" + value + "\"";
            } else {
                return value.toString();
            }
        }).collect(toList());
    }
}

