package dev.langchain4j.store.embedding.filter;

import dev.langchain4j.Experimental;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.comparison.*;
import dev.langchain4j.store.embedding.filter.logical.And;
import dev.langchain4j.store.embedding.filter.logical.Not;
import dev.langchain4j.store.embedding.filter.logical.Or;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static java.util.Arrays.asList;
import static java.util.Arrays.stream;
import static java.util.stream.Collectors.toList;

/**
 * This class represents a filter that can be applied during search in an {@link EmbeddingStore}.
 * <br>
 * Many {@link EmbeddingStore}s support a feature called metadata filtering. A {@code Filter} can be used for this.
 * <br>
 * A {@code Filter} object can represent simple (e.g. {@code type = 'documentation'})
 * and composite (e.g. {@code type = 'documentation' AND year > 2020}) filter expressions in
 * an {@link EmbeddingStore}-agnostic way.
 * <br>
 * Each {@link EmbeddingStore} implementation that supports metadata filtering is mapping {@link Filter}
 * into it's native filter expression.
 *
 * @see Equal
 * @see NotEqual
 * @see GreaterThan
 * @see GreaterThanOrEqual
 * @see LessThan
 * @see LessThanOrEqual
 * @see In
 * @see NotIn
 * @see And
 * @see Not
 * @see Or
 */
@Experimental
public interface Filter {

    /**
     * Tests if a given object satisfies this {@link Filter}.
     *
     * @param object An object to test.
     * @return {@code true} if a given object satisfies this {@link Filter}, {@code false} otherwise.
     */
    boolean test(Object object);

    default Filter and(Filter filter) {
        return and(this, filter);
    }

    static Filter and(Filter left, Filter right) {
        return new And(left, right);
    }

    default Filter or(Filter filter) {
        return or(this, filter);
    }

    static Filter or(Filter left, Filter right) {
        return new Or(left, right);
    }

    static Filter not(Filter expression) {
        return new Not(expression);
    }

    class Key {

        private final String key;

        public Key(String key) {
            this.key = ensureNotBlank(key, "key");
        }

        @Experimental
        public static Key key(String key) {
            return new Key(key);
        }

        // eq

        public Filter eq(String value) {
            return new Equal(key, value);
        }

        public Filter eq(int value) {
            return new Equal(key, value);
        }

        public Filter eq(long value) {
            return new Equal(key, value);
        }

        public Filter eq(float value) {
            return new Equal(key, value);
        }

        public Filter eq(double value) {
            return new Equal(key, value);
        }

        // ne

        public Filter ne(String value) {
            return new NotEqual(key, value);
        }

        public Filter ne(int value) {
            return new NotEqual(key, value);
        }

        public Filter ne(long value) {
            return new NotEqual(key, value);
        }

        public Filter ne(float value) {
            return new NotEqual(key, value);
        }

        public Filter ne(double value) {
            return new NotEqual(key, value);
        }

        // gt

        public Filter gt(String value) {
            return new GreaterThan(key, value);
        }

        public Filter gt(int value) {
            return new GreaterThan(key, value);
        }

        public Filter gt(long value) {
            return new GreaterThan(key, value);
        }

        public Filter gt(float value) {
            return new GreaterThan(key, value);
        }

        public Filter gt(double value) {
            return new GreaterThan(key, value);
        }

        // gte

        public Filter gte(String value) {
            return new GreaterThanOrEqual(key, value);
        }

        public Filter gte(int value) {
            return new GreaterThanOrEqual(key, value);
        }

        public Filter gte(long value) {
            return new GreaterThanOrEqual(key, value);
        }

        public Filter gte(float value) {
            return new GreaterThanOrEqual(key, value);
        }

        public Filter gte(double value) {
            return new GreaterThanOrEqual(key, value);
        }

        // lt

        public Filter lt(String value) {
            return new LessThan(key, value);
        }

        public Filter lt(int value) {
            return new LessThan(key, value);
        }

        public Filter lt(long value) {
            return new LessThan(key, value);
        }

        public Filter lt(float value) {
            return new LessThan(key, value);
        }

        public Filter lt(double value) {
            return new LessThan(key, value);
        }

        // lte

        public Filter lte(String value) {
            return new LessThanOrEqual(key, value);
        }

        public Filter lte(int value) {
            return new LessThanOrEqual(key, value);
        }

        public Filter lte(long value) {
            return new LessThanOrEqual(key, value);
        }

        public Filter lte(float value) {
            return new LessThanOrEqual(key, value);
        }

        public Filter lte(double value) {
            return new LessThanOrEqual(key, value);
        }

        // in

        public Filter in(String... values) {
            return new In(key, asList(values));
        }

        public Filter in(int... values) {
            return new In(key, stream(values).boxed().collect(toList()));
        }

        public Filter in(long... values) {
            return new In(key, stream(values).boxed().collect(toList()));
        }

        public Filter in(float... values) {
            List<Float> valuesList = new ArrayList<>();
            for (float value : values) {
                valuesList.add(value);
            }
            return new In(key, valuesList);
        }

        public Filter in(double... values) {
            return new In(key, stream(values).boxed().collect(toList()));
        }

        public Filter in(Collection<?> values) {
            return new In(key, values);
        }

        // nin

        public Filter nin(String... values) {
            return new NotIn(key, asList(values));
        }

        public Filter nin(int... values) {
            return new NotIn(key, stream(values).boxed().collect(toList()));
        }

        public Filter nin(long... values) {
            return new NotIn(key, stream(values).boxed().collect(toList()));
        }

        public Filter nin(float... values) {
            List<Float> valuesList = new ArrayList<>();
            for (float value : values) {
                valuesList.add(value);
            }
            return new NotIn(key, valuesList);
        }

        public Filter nin(double... values) {
            return new NotIn(key, stream(values).boxed().collect(toList()));
        }

        public Filter nin(Collection<?> values) {
            return new NotIn(key, values);
        }
    }
}