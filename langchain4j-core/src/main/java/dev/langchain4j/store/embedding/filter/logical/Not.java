package dev.langchain4j.store.embedding.filter.logical;

import dev.langchain4j.store.embedding.filter.Filter;

import java.util.Objects;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

public class Not implements Filter {

    private final Filter expression;

    public Not(Filter expression) {
        this.expression = ensureNotNull(expression, "expression");
    }

    public Filter expression() {
        return expression;
    }

    @Override
    public boolean test(Object object) {
        return !expression.test(object);
    }

    public boolean equals(final Object o) {
        if (o == this) return true;
        if (!(o instanceof Not other)) return false;
        return Objects.equals(this.expression, other.expression);
    }

    public int hashCode() {
        return Objects.hash(expression);
    }

    public String toString() {
        return "Not(expression=" + this.expression + ")";
    }
}
