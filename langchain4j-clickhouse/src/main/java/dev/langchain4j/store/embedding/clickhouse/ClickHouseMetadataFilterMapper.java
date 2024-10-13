package dev.langchain4j.store.embedding.clickhouse;

import com.clickhouse.data.ClickHouseDataType;
import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.comparison.*;
import dev.langchain4j.store.embedding.filter.logical.And;
import dev.langchain4j.store.embedding.filter.logical.Not;
import dev.langchain4j.store.embedding.filter.logical.Or;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

import static dev.langchain4j.store.embedding.clickhouse.ClickHouseMappingKey.ID_MAPPING_KEY;
import static dev.langchain4j.store.embedding.clickhouse.ClickHouseMappingKey.TEXT_MAPPING_KEY;

class ClickHouseMetadataFilterMapper {

    private final Map<String, String> columnMap;
    private final Map<String, ClickHouseDataType> typeMap;

    ClickHouseMetadataFilterMapper(Map<String, String> columnMap,
                                   Map<String, ClickHouseDataType> typeMap) {
        this.columnMap = columnMap;
        this.typeMap = typeMap;
    }

    String map(Filter filter) {
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

    private String mapEqual(IsEqualTo isEqualTo) {
        return mapBinaryOperation(isEqualTo.key(), "==", handleSingleValue(isEqualTo.key(), isEqualTo.comparisonValue()));
    }

    private String mapNotEqual(IsNotEqualTo isNotEqualTo) {
        return mapBinaryOperation(
                mapBinaryOperation(isNotEqualTo.key(), "<>", handleSingleValue(isNotEqualTo.key(), isNotEqualTo.comparisonValue())),
                "OR",
                mapIsNull(isNotEqualTo.key())
        );
    }

    private String mapGreaterThan(IsGreaterThan isGreaterThan) {
        return mapBinaryOperation(isGreaterThan.key(), ">", handleSingleValue(isGreaterThan.key(), isGreaterThan.comparisonValue()));
    }

    private String mapGreaterThanOrEqual(IsGreaterThanOrEqualTo isGreaterThanOrEqualTo) {
        return mapBinaryOperation(isGreaterThanOrEqualTo.key(), ">=", handleSingleValue(isGreaterThanOrEqualTo.key(), isGreaterThanOrEqualTo.comparisonValue()));
    }

    private String mapLessThan(IsLessThan isLessThan) {
        return mapBinaryOperation(isLessThan.key(), "<", handleSingleValue(isLessThan.key(), isLessThan.comparisonValue()));
    }

    private String mapLessThanOrEqual(IsLessThanOrEqualTo isLessThanOrEqualTo) {
        return mapBinaryOperation(isLessThanOrEqualTo.key(), "<=", handleSingleValue(isLessThanOrEqualTo.key(), isLessThanOrEqualTo.comparisonValue()));
    }

    public String mapIn(IsIn isIn) {
        return mapBinaryOperation(isIn.key(), "IN", handleMultiValue(isIn.key(), isIn.comparisonValues()));
    }

    public String mapNotIn(IsNotIn isNotIn) {
        return mapBinaryOperation(
                mapBinaryOperation(isNotIn.key(), "NOT IN", handleMultiValue(isNotIn.key(), isNotIn.comparisonValues())),
                "OR",
                mapIsNull(isNotIn.key())
        );
    }

    private String mapAnd(And and) {
        return mapBinaryOperation(map(and.left()), "AND", map(and.right()));
    }

    private String mapNot(Not not) {
        String expression = map(not.expression());
        return mapBinaryOperation(
                mapUnaryOperation("NOT", expression),
                "OR",
                mapIsNull(expression)
        );
    }

    private String mapOr(Or or) {
        return mapBinaryOperation(map(or.left()), "OR", map(or.right()));
    }

    private String handleSingleValue(String key, Object value) {
        boolean isIdOrText = key.equals(columnMap.get(ID_MAPPING_KEY)) || key.equals(columnMap.get(TEXT_MAPPING_KEY));
        if (isIdOrText || typeMap.get(key) == ClickHouseDataType.UUID || typeMap.get(key) == ClickHouseDataType.String) {
            value = "'" + value + "'";
        }

        return value.toString();
    }

    private String handleMultiValue(String key, Collection<?> values) {
        boolean isIdOrText = key.equals(columnMap.get(ID_MAPPING_KEY)) || key.equals(columnMap.get(TEXT_MAPPING_KEY));
        if (isIdOrText || typeMap.get(key) == ClickHouseDataType.UUID || typeMap.get(key) == ClickHouseDataType.String) {
            values = values.stream()
                    .map(value -> "'" + value + "'")
                    .collect(Collectors.toList());
        }

        return "[" + values.stream().map(Object::toString).collect(Collectors.joining(",")) + "]";
    }

    private String mapBinaryOperation(String left, String operator, String right) {
        return "(" + String.join(" ", left, operator, right) + ")";
    }

    private String mapIsNull(String key) {
        return mapUnaryOperation(key, "IS NULL");
    }

    private String mapUnaryOperation(String firstEle, String secondEle) {
        return "(" + String.join(" ", firstEle, secondEle) + ")";
    }
}
