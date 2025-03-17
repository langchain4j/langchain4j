package dev.langchain4j.store.embedding.filter.logical;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.store.embedding.filter.Filter;
import java.util.Objects;

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
        if (!(object instanceof Metadata)) {
            return false;
        }
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
