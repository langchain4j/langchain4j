package dev.langchain4j.store.embedding.filter.logical;

import dev.langchain4j.store.embedding.filter.Filter;

import java.util.Objects;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

public class And implements Filter {

    private final Filter left;
    private final Filter right;

    public And(Filter left, Filter right) {
        this.left = ensureNotNull(left, "left");
        this.right = ensureNotNull(right, "right");
    }

    public Filter left() {
        return left;
    }

    public Filter right() {
        return right;
    }

    @Override
    public boolean test(Object object) {
        return left().test(object) && right().test(object);
    }

    public boolean equals(final Object o) {
        if (o == this) return true;
        if (!(o instanceof And other)) return false;
        return Objects.equals(this.left, other.left) && Objects.equals(this.right, other.right);
    }

    public int hashCode() {
        return Objects.hash(left, right);
    }

    public String toString() {
        return "And(left=" + this.left + ", right=" + this.right + ")";
    }
}
