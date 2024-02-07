package dev.langchain4j.store.embedding.filter;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.comparison.*;
import dev.langchain4j.store.embedding.filter.logical.And;
import dev.langchain4j.store.embedding.filter.logical.Not;
import dev.langchain4j.store.embedding.filter.logical.Or;

import java.util.Collection;

import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static java.util.Arrays.asList;

/**
 * This is a base interface for a {@link Metadata} filter.
 * It allows representing simple (e.g. {@code type = "documentation"})
 * and complex (e.g. {@code type in ("documentation", "tutorial") AND year > 2020}) filter expressions in
 * an {@link EmbeddingStore}-agnostic way.
 * <br>
 * Each {@link EmbeddingStore} implementation that supports metadata filtering is mapping {@link MetadataFilter}
 * into it's native filter expression.
 * TODO
 */
public interface MetadataFilter { // TODO extends Predicate<Metadata> ?

    /**
     * Tests if a given {@link Metadata} satisfies this {@link MetadataFilter}.
     *
     * @param metadata {@link Metadata} to test.
     * @return true if {@link Metadata} satisfies this {@link MetadataFilter}, false otherwise.
     */
    boolean test(Metadata metadata);

    static MetadataFilter and(MetadataFilter left, MetadataFilter right) {
        return new And(left, right);
    }

    static MetadataFilter or(MetadataFilter left, MetadataFilter right) {
        return new Or(left, right);
    }

    static MetadataFilter not(MetadataFilter expression) {
        return new Not(expression);
    }

    class MetadataKey {

        private final String key;

        public MetadataKey(String key) {
            this.key = ensureNotBlank(key, "key");
        }

        public static MetadataKey key(String key) {
            return new MetadataKey(key);
        }

        public MetadataFilter eq(String value) {
            return new Equal(key, value);
        }

        public MetadataFilter eq(Integer value) {
            return new Equal(key, value);
        }

        public MetadataFilter eq(Long value) {
            return new Equal(key, value);
        }

        //TODO more types, everywhere

        public MetadataFilter eq(Double value) {
            return new Equal(key, value);
        }

        public MetadataFilter eq(Boolean value) {
            return new Equal(key, value);
        }

        public MetadataFilter ne(String value) {
            return new NotEqual(key, value);
        }

        public MetadataFilter ne(Integer value) {
            return new NotEqual(key, value);
        }

        public MetadataFilter ne(Double value) {
            return new NotEqual(key, value);
        }

        public MetadataFilter ne(Boolean value) {
            return new NotEqual(key, value);
        }

        public MetadataFilter gt(String value) {
            return new GreaterThan(key, value);
        }

        public MetadataFilter gt(Integer value) {
            return new GreaterThan(key, value);
        }

        public MetadataFilter gt(Double value) {
            return new GreaterThan(key, value);
        }

        public MetadataFilter gt(Boolean value) {
            return new GreaterThan(key, value);
        }

        public MetadataFilter gte(String value) {
            return new GreaterThanOrEqual(key, value);
        }

        public MetadataFilter gte(Integer value) {
            return new GreaterThanOrEqual(key, value);
        }

        public MetadataFilter gte(Double value) {
            return new GreaterThanOrEqual(key, value);
        }

        public MetadataFilter gte(Boolean value) {
            return new GreaterThanOrEqual(key, value);
        }

        public MetadataFilter lt(String value) {
            return new LessThan(key, value);
        }

        public MetadataFilter lt(Integer value) {
            return new LessThan(key, value);
        }

        public MetadataFilter lt(Double value) {
            return new LessThan(key, value);
        }

        public MetadataFilter lt(Boolean value) {
            return new LessThan(key, value);
        }

        public MetadataFilter lte(String value) {
            return new LessThanOrEqual(key, value);
        }

        public MetadataFilter lte(Integer value) {
            return new LessThanOrEqual(key, value);
        }

        public MetadataFilter lte(Double value) {
            return new LessThanOrEqual(key, value);
        }

        public MetadataFilter lte(Boolean value) {
            return new LessThanOrEqual(key, value);
        }

        public MetadataFilter in(String... values) {
            return new In(key, asList(values));
        }

        public MetadataFilter in(Integer... values) {
            return new In(key, asList(values));
        }

        public MetadataFilter in(Double... values) {
            return new In(key, asList(values));
        }

        public MetadataFilter in(Boolean... values) {
            return new In(key, asList(values));
        }

        public MetadataFilter in(Collection<?> values) {
            return new In(key, values);
        }

        public MetadataFilter nin(String... values) {
            return new NotIn(key, asList(values));
        }

        public MetadataFilter nin(Integer... values) {
            return new NotIn(key, asList(values));
        }

        public MetadataFilter nin(Double... values) {
            return new NotIn(key, asList(values));
        }

        public MetadataFilter nin(Boolean... values) {
            return new NotIn(key, asList(values));
        }

        public MetadataFilter nin(Collection<?> values) {
            return new NotIn(key, values);
        }
    }
}