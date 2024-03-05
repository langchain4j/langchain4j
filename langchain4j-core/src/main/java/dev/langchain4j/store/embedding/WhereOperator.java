package dev.langchain4j.store.embedding;

/**
 * @author nottyjay
 */
public enum WhereOperator {

    EQ("eq"),
    NE("ne"),
    GT("gt"),
    LT("lt"),
    GTE("gte"),
    LTE("lte"),
    AND("and"),
    IN("in"),
    NOT_IN("not in"),
    OR("or");

    private final String value;

    WhereOperator(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static WhereOperator from(String value) {
        for (WhereOperator operator : WhereOperator.values()) {
            if (operator.value().equals(value)) {
                return operator;
            }
        }
        throw new IllegalArgumentException("Invalid value for WhereOperator: " + value);
    }
}
