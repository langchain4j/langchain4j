package dev.langchain4j.store.embedding.qdrant;

import io.qdrant.client.ConditionFactory;
import dev.langchain4j.store.embedding.filter.Filter;

import dev.langchain4j.store.embedding.filter.comparison.*;
import dev.langchain4j.store.embedding.filter.logical.And;
import dev.langchain4j.store.embedding.filter.logical.Not;
import dev.langchain4j.store.embedding.filter.logical.Or;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import io.qdrant.client.grpc.Points;
import io.qdrant.client.grpc.Points.Condition;

class QdrantFilterConverter {

    public static Points.Filter convertExpression(Filter expression) {
        return QdrantFilterConverter.convertOperand(expression);
    }

    private static Points.Filter convertOperand(Filter operand) {
        Points.Filter.Builder context = Points.Filter.newBuilder();
        List<Condition> mustClauses = new ArrayList<>();
        List<Condition> shouldClauses = new ArrayList<>();
        List<Condition> mustNotClauses = new ArrayList<>();

        if (operand instanceof Not not) {
            mustNotClauses.add(ConditionFactory.filter(convertOperand(not.expression())));
        } else if (operand instanceof And and) {
            mustClauses.add(ConditionFactory.filter(convertOperand(and.left())));
            mustClauses.add(ConditionFactory.filter(convertOperand(and.right())));
        } else if (operand instanceof Or or) {
            shouldClauses.add(ConditionFactory.filter(convertOperand(or.left())));
            shouldClauses.add(ConditionFactory.filter(convertOperand(or.right())));
        } else {
            mustClauses.add(parseComparison(operand));
        }

        return context.addAllMust(mustClauses).addAllShould(shouldClauses).addAllMustNot(mustNotClauses).build();
    }

    private static Condition parseComparison(Filter comparision) {
        if (comparision instanceof IsEqualTo to) {
            return buildEqCondition(to);
        } else if (comparision instanceof IsNotEqualTo to) {
            return buildNeCondition(to);
        } else if (comparision instanceof IsGreaterThan than) {
            return buildGtCondition(than);
        } else if (comparision instanceof IsGreaterThanOrEqualTo to) {
            return buildGteCondition(to);
        } else if (comparision instanceof IsLessThan than) {
            return buildLtCondition(than);
        } else if (comparision instanceof IsLessThanOrEqualTo to) {
            return buildLteCondition(to);
        } else if (comparision instanceof IsIn in) {
            return buildInCondition(in);
        } else if (comparision instanceof IsNotIn in) {
            return buildNInCondition(in);
        } else {
            throw new UnsupportedOperationException("Unsupported comparision type: " + comparision);
        }
    }

    private static Condition buildEqCondition(IsEqualTo equalTo) {
        String key = equalTo.key();
        Object value = equalTo.comparisonValue();
        if (value instanceof String || value instanceof UUID) {
            return ConditionFactory.matchKeyword(key, value.toString());
        } else if (value instanceof Boolean boolean1) {
            return ConditionFactory.match(key, boolean1);
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
            return ConditionFactory.filter(
                    Points.Filter.newBuilder().addMustNot(ConditionFactory.matchKeyword(key, value.toString()))
                            .build());
        } else if (value instanceof Boolean boolean1) {
            Condition condition = ConditionFactory.match(key, boolean1);
            return ConditionFactory.filter(Points.Filter.newBuilder().addMustNot(condition).build());
        } else if (value instanceof Integer || value instanceof Long) {
            long lValue = Long.parseLong(value.toString());
            Condition condition = ConditionFactory.match(key, lValue);
            return ConditionFactory.filter(Points.Filter.newBuilder().addMustNot(condition).build());
        }

        throw new IllegalArgumentException(
                "Invalid value type for IsNotEqualto. Can either be a String or Boolean or Integer or Long");

    }

    private static Condition buildGtCondition(IsGreaterThan greaterThan) {
        String key = greaterThan.key();
        Object value = greaterThan.comparisonValue();
        if (value instanceof Number) {
            double dvalue = Double.parseDouble(value.toString());
            return ConditionFactory.range(key, Points.Range.newBuilder().setGt(dvalue).build());
        }
        throw new RuntimeException("Unsupported value type for IsGreaterThan condition. Only supports Number");

    }

    private static Condition buildLtCondition(IsLessThan lessThan) {
        String key = lessThan.key();
        Object value = lessThan.comparisonValue();
        if (value instanceof Number) {
            double dvalue = Double.parseDouble(value.toString());
            return ConditionFactory.range(key, Points.Range.newBuilder().setLt(dvalue).build());
        }
        throw new RuntimeException("Unsupported value type for IsLessThan condition. Only supports Number");

    }

    private static Condition buildGteCondition(IsGreaterThanOrEqualTo greaterThanOrEqualTo) {
        String key = greaterThanOrEqualTo.key();
        Object value = greaterThanOrEqualTo.comparisonValue();
        if (value instanceof Number) {
            double dvalue = Double.parseDouble(value.toString());
            return ConditionFactory.range(key, Points.Range.newBuilder().setGte(dvalue).build());
        }
        throw new RuntimeException("Unsupported value type for IsGreaterThanOrEqualTo condition. Only supports Number");

    }

    private static Condition buildLteCondition(IsLessThanOrEqualTo lessThanOrEqualTo) {
        String key = lessThanOrEqualTo.key();
        Object value = lessThanOrEqualTo.comparisonValue();
        if (value instanceof Number) {
            double dvalue = Double.parseDouble(value.toString());
            return ConditionFactory.range(key, Points.Range.newBuilder().setLte(dvalue).build());
        }
        throw new RuntimeException("Unsupported value type for IsLessThanOrEqualTo condition. Only supports Number");

    }

    private static Condition buildInCondition(IsIn in) {
        String key = in.key();
        List<?> valueList = new ArrayList<>(in.comparisonValues());

        Object firstValue = valueList.get(0);
        if (firstValue instanceof String || firstValue instanceof UUID) {
            // If the first value is a string, then all values should be strings
            List<String> stringValues = new ArrayList<>();
            for (Object valueObj : valueList) {
                stringValues.add(valueObj.toString());
            }
            return ConditionFactory.matchKeywords(key, stringValues);
        } else if (firstValue instanceof Integer || firstValue instanceof Long) {
            // If the first value is a number, then all values should be numbers
            List<Long> longValues = new ArrayList<>();
            for (Object valueObj : valueList) {
                Long longValue = Long.parseLong(valueObj.toString());
                longValues.add(longValue);
            }
            return ConditionFactory.matchValues(key, longValues);
        } else {
            throw new RuntimeException(
                    "Unsupported value in IsIn value list. Only supports String or Integer or Long");
        }

    }

    private static Condition buildNInCondition(IsNotIn notIn) {
        String key = notIn.key();
        List<?> valueList = new ArrayList<>(notIn.comparisonValues());
        Object firstValue = valueList.get(0);

        if (firstValue instanceof String || firstValue instanceof UUID) {
            // If the first value is a string, then all values should be strings
            List<String> stringValues = new ArrayList<>();
            for (Object valueObj : valueList) {
                stringValues.add(valueObj.toString());
            }
            return ConditionFactory.matchExceptKeywords(key, stringValues);
        } else if (firstValue instanceof Integer || firstValue instanceof Long) {
            // If the first value is a number, then all values should be numbers
            List<Long> longValues = new ArrayList<>();
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