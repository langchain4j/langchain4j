package dev.langchain4j.store.embedding.filter.logical;

import dev.langchain4j.store.embedding.filter.Filter;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

@ToString
@EqualsAndHashCode
public abstract class BinaryFilter implements Filter {

    private final Filter left;
    private final Filter right;

    protected BinaryFilter(Filter left, Filter right) {
        this.left = ensureNotNull(left, "left");
        this.right = ensureNotNull(right, "right");
    }

    public Filter left() {
        return left;
    }

    public Filter right() {
        return right;
    }
}