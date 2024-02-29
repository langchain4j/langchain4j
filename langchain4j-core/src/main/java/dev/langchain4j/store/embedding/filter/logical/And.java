package dev.langchain4j.store.embedding.filter.logical;

import dev.langchain4j.store.embedding.filter.Filter;

public class And extends BinaryFilter {

    public And(Filter left, Filter right) {
        super(left, right);
    }

    @Override
    public boolean test(Object object) {
        return left().test(object) && right().test(object);
    }
}
