package dev.langchain4j.store.embedding.qdrant;

import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.comparison.*;
import dev.langchain4j.store.embedding.filter.logical.And;
import dev.langchain4j.store.embedding.filter.logical.Not;
import dev.langchain4j.store.embedding.filter.logical.Or;
import io.qdrant.client.ConditionFactory;
import io.qdrant.client.grpc.Points;
import io.qdrant.client.grpc.Points.Condition;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

class QdrantFilterConverter {

    public static Points.Filter convertExpression(Filter expression) {
        return QdrantFilterConverter.convertOperand(expression);
    }

    private static Points.Filter convertOperand(Filter operand) {
        Points.Filter.Builder context = Points.Filter.newBuilder();
        List<Condition> mustClauses = new ArrayList<Condition>();
        List<Condition> shouldClauses = new ArrayList<Condition>();
        List<Condition> mustNotClauses = new ArrayList<Condition>();

        if (operand instanceof Not) {
            Not not = (Not) operand;
            mustNotClauses.add(ConditionFactory.filter(convertOperand(not.expression())));
        } else if (operand instanceof And) {
            And and = (And) operand;
            mustClauses.add(ConditionFactory.filter(convertOperand(and.left())));
            mustClauses.add(ConditionFactory.filter(convertOperand(and.right())));
        } else if (operand instanceof Or) {
            Or or = (Or) operand;
            shouldClauses.add(ConditionFactory.filter(convertOperand(or.left())));
            shouldClauses.add(ConditionFactory.filter(convertOperand(or.right())));
        } else {
            mustClauses.add(convert(operand));
        }

        return context.addAllMust(mustClauses)
                .addAllShould(shouldClauses)
                .addAllMustNot(mustNotClauses)
                .build();
    }

    private static Condition convert(Filter filter) {
        if (filter instanceof ContainsString containsString) {
            return buildContainsCondition(containsString);
        } else if (filter instanceof IsEqualTo isEqualTo) {
            return buildEqCondition(isEqualTo);
        } else if (filter instanceof IsNotEqualTo isNotEqualTo) {
            return buildNeCondition(isNotEqualTo);
        } else if (filter instanceof IsGreaterThan isGreaterThan) {
            return buildGtCondition(isGreaterThan);
        } else if (filter instanceof IsGreaterThanOrEqualTo isGreaterThanOrEqualTo) {
            return buildGteCondition(isGreaterThanOrEqualTo);
        } else if (filter instanceof IsLessThan isLessThan) {
            return buildLtCondition(isLessThan);
        } else if (filter instanceof IsLessThanOrEqualTo isLessThanOrEqualTo) {
            return buildLteCondition(isLessThanOrEqualTo);
        } else if (filter instanceof IsIn isIn) {
            return buildInCondition(isIn);
        } else if (filter instanceof IsNotIn isNotIn) {
            return buildNInCondition(isNotIn);
        } else {
            throw new UnsupportedOperationException("Unsupported filter type: " + filter.getClass().getName());
        }
    }

    private static Condition buildContainsCondition(ContainsString containsString) {
        return ConditionFactory.matchText(containsString.key(), containsString.comparisonValue());
    }

    private static Condition buildEqCondition(IsEqualTo equalTo) {
        String key = equalTo.key();
        Object value = equalTo.comparisonValue();
        if (value instanceof String || value instanceof UUID) {
            return ConditionFactory.matchKeyword(key, value.toString());
        } else if (value instanceof Boolean) {
            return ConditionFactory.match(key, (Boolean) value);
        } else if (value instanceof Integer || value instanceof Long) {
            long lValue = Long.parseLong(value.toString());
            return ConditionFactory.match(key, lValue);
        }

        throw new IllegalArgumentException(
                "Invalid value type for IsEqualTo. Can either be a String or Boolean or Integer or Long");
    }

    private static Condition buildNeCondition(IsNotEqualTo notEqual) {
        String key = notEqual.key();
        Object value = notEqual.comparisonValue();
        if (value instanceof String || value instanceof UUID) {
            return ConditionFactory.filter(Points.Filter.newBuilder()
                    .addMustNot(ConditionFactory.matchKeyword(key, value.toString()))
                    .build());
        } else if (value instanceof Boolean) {
            Condition condition = ConditionFactory.match(key, (Boolean) value);
            return ConditionFactory.filter(
                    Points.Filter.newBuilder().addMustNot(condition).build());
        } else if (value instanceof Integer || value instanceof Long) {
            long lValue = Long.parseLong(value.toString());
            Condition condition = ConditionFactory.match(key, lValue);
            return ConditionFactory.filter(
                    Points.Filter.newBuilder().addMustNot(condition).build());
        }

        throw new IllegalArgumentException(
                "Invalid value type for IsNotEqualto. Can either be a String or Boolean or Integer or Long");
    }

    private static Condition buildGtCondition(IsGreaterThan greaterThan) {
        String key = greaterThan.key();
        Object value = greaterThan.comparisonValue();
        if (value instanceof Number) {
            Double dvalue = Double.parseDouble(value.toString());
            return ConditionFactory.range(
                    key, Points.Range.newBuilder().setGt(dvalue).build());
        }
        throw new RuntimeException("Unsupported value type for IsGreaterThan condition. Only supports Number");
    }

    private static Condition buildLtCondition(IsLessThan lessThan) {
        String key = lessThan.key();
        Object value = lessThan.comparisonValue();
        if (value instanceof Number) {
            Double dvalue = Double.parseDouble(value.toString());
            return ConditionFactory.range(
                    key, Points.Range.newBuilder().setLt(dvalue).build());
        }
        throw new RuntimeException("Unsupported value type for IsLessThan condition. Only supports Number");
    }

    private static Condition buildGteCondition(IsGreaterThanOrEqualTo greaterThanOrEqualTo) {
        String key = greaterThanOrEqualTo.key();
        Object value = greaterThanOrEqualTo.comparisonValue();
        if (value instanceof Number) {
            Double dvalue = Double.parseDouble(value.toString());
            return ConditionFactory.range(
                    key, Points.Range.newBuilder().setGte(dvalue).build());
        }
        throw new RuntimeException("Unsupported value type for IsGreaterThanOrEqualTo condition. Only supports Number");
    }

    private static Condition buildLteCondition(IsLessThanOrEqualTo lessThanOrEqualTo) {
        String key = lessThanOrEqualTo.key();
        Object value = lessThanOrEqualTo.comparisonValue();
        if (value instanceof Number) {
            Double dvalue = Double.parseDouble(value.toString());
            return ConditionFactory.range(
                    key, Points.Range.newBuilder().setLte(dvalue).build());
        }
        throw new RuntimeException("Unsupported value type for IsLessThanOrEqualTo condition. Only supports Number");
    }

    private static Condition buildInCondition(IsIn in) {
        String key = in.key();
        List<?> valueList = new ArrayList<>(in.comparisonValues());

        Object firstValue = valueList.get(0);
        if (firstValue instanceof String || firstValue instanceof UUID) {
            // If the first value is a string, then all values should be strings
            List<String> stringValues = new ArrayList<String>();
            for (Object valueObj : valueList) {
                stringValues.add(valueObj.toString());
            }
            return ConditionFactory.matchKeywords(key, stringValues);
        } else if (firstValue instanceof Integer || firstValue instanceof Long) {
            // If the first value is a number, then all values should be numbers
            List<Long> longValues = new ArrayList<Long>();
            for (Object valueObj : valueList) {
                Long longValue = Long.parseLong(valueObj.toString());
                longValues.add(longValue);
            }
            return ConditionFactory.matchValues(key, longValues);
        } else {
            throw new RuntimeException("Unsupported value in IsIn value list. Only supports String or Integer or Long");
        }
    }

    private static Condition buildNInCondition(IsNotIn notIn) {
        String key = notIn.key();
        List<?> valueList = new ArrayList<>(notIn.comparisonValues());
        Object firstValue = valueList.get(0);

        if (firstValue instanceof String || firstValue instanceof UUID) {
            // If the first value is a string, then all values should be strings
            List<String> stringValues = new ArrayList<String>();
            for (Object valueObj : valueList) {
                stringValues.add(valueObj.toString());
            }
            return ConditionFactory.matchExceptKeywords(key, stringValues);
        } else if (firstValue instanceof Integer || firstValue instanceof Long) {
            // If the first value is a number, then all values should be numbers
            List<Long> longValues = new ArrayList<Long>();
            for (Object valueObj : valueList) {
                Long longValue = Long.parseLong(valueObj.toString());
                longValues.add(longValue);
            }
            return ConditionFactory.matchExceptValues(key, longValues);
        } else {
            throw new RuntimeException(
                    "Unsupported value in IsNotIn value list. Only supports String or Integer or Long");
        }
    }
}
