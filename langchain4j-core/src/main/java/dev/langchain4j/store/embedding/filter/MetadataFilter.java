package dev.langchain4j.store.embedding.filter;

import dev.langchain4j.Experimental;
import dev.langchain4j.data.document.Metadata;
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
 * This is a base interface for a {@link Metadata} filter.
 * It allows representing simple (e.g. {@code type = "documentation"})
 * and complex (e.g. {@code type in ("documentation", "tutorial") AND year > 2020}) filter expressions in
 * an {@link EmbeddingStore}-agnostic way.
 * <br>
 * Each {@link EmbeddingStore} implementation that supports metadata filtering is mapping {@link MetadataFilter}
 * into it's native filter expression.
 * TODO
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
public interface MetadataFilter {

    /**
     * Tests if a given {@link Metadata} satisfies this {@link MetadataFilter}.
     *
     * @param metadata {@link Metadata} to test.
     * @return true if {@link Metadata} satisfies this {@link MetadataFilter}, false otherwise.
     */
    boolean test(Metadata metadata); // TODO move to in-memory?

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

        public static MetadataKey key(String key) { // TODO name
            return new MetadataKey(key);
        }

        // eq

        public MetadataFilter eq(String value) {
            return new Equal(key, value);
        }

        public MetadataFilter eq(int value) {
            return new Equal(key, value);
        }

        public MetadataFilter eq(long value) {
            return new Equal(key, value);
        }

        public MetadataFilter eq(float value) {
            return new Equal(key, value);
        }

        public MetadataFilter eq(double value) {
            return new Equal(key, value);
        }

        // ne

        public MetadataFilter ne(String value) {
            return new NotEqual(key, value);
        }

        public MetadataFilter ne(int value) {
            return new NotEqual(key, value);
        }

        public MetadataFilter ne(long value) {
            return new NotEqual(key, value);
        }

        public MetadataFilter ne(float value) {
            return new NotEqual(key, value);
        }

        public MetadataFilter ne(double value) {
            return new NotEqual(key, value);
        }

        // gt

        public MetadataFilter gt(String value) {
            return new GreaterThan(key, value);
        }

        public MetadataFilter gt(int value) {
            return new GreaterThan(key, value);
        }

        public MetadataFilter gt(long value) {
            return new GreaterThan(key, value);
        }

        public MetadataFilter gt(float value) {
            return new GreaterThan(key, value);
        }

        public MetadataFilter gt(double value) {
            return new GreaterThan(key, value);
        }

        // gte

        public MetadataFilter gte(String value) {
            return new GreaterThanOrEqual(key, value);
        }

        public MetadataFilter gte(int value) {
            return new GreaterThanOrEqual(key, value);
        }

        public MetadataFilter gte(long value) {
            return new GreaterThanOrEqual(key, value);
        }

        public MetadataFilter gte(float value) {
            return new GreaterThanOrEqual(key, value);
        }

        public MetadataFilter gte(double value) {
            return new GreaterThanOrEqual(key, value);
        }

        // lt

        public MetadataFilter lt(String value) {
            return new LessThan(key, value);
        }

        public MetadataFilter lt(int value) {
            return new LessThan(key, value);
        }

        public MetadataFilter lt(long value) {
            return new LessThan(key, value);
        }

        public MetadataFilter lt(float value) {
            return new LessThan(key, value);
        }

        public MetadataFilter lt(double value) {
            return new LessThan(key, value);
        }

        // lte

        public MetadataFilter lte(String value) {
            return new LessThanOrEqual(key, value);
        }

        public MetadataFilter lte(int value) {
            return new LessThanOrEqual(key, value);
        }

        public MetadataFilter lte(long value) {
            return new LessThanOrEqual(key, value);
        }

        public MetadataFilter lte(float value) {
            return new LessThanOrEqual(key, value);
        }

        public MetadataFilter lte(double value) {
            return new LessThanOrEqual(key, value);
        }

        // in

        public MetadataFilter in(String... values) {
            return new In(key, asList(values));
        }

        public MetadataFilter in(int... values) { // TODO test without args
            return new In(key, stream(values).boxed().collect(toList()));
        }

        public MetadataFilter in(long... values) { // TODO test without args
            return new In(key, stream(values).boxed().collect(toList()));
        }

        public MetadataFilter in(float... values) { // TODO test without args
            List<Float> valuesList = new ArrayList<>();
            for (float value : values) {
                valuesList.add(value);
            }
            return new In(key, valuesList);
        }

        public MetadataFilter in(double... values) {
            return new In(key, stream(values).boxed().collect(toList()));
        }

        public MetadataFilter in(Collection<?> values) {
            // TODO test all values of same type? not null? of supported types only?
            return new In(key, values);
        }

        // nin

        public MetadataFilter nin(String... values) {
            return new NotIn(key, asList(values));
        }

        public MetadataFilter nin(int... values) { // TODO test without args
            return new NotIn(key, stream(values).boxed().collect(toList()));
        }

        public MetadataFilter nin(long... values) { // TODO test without args
            return new NotIn(key, stream(values).boxed().collect(toList()));
        }

        public MetadataFilter nin(float... values) { // TODO test without args
            List<Float> valuesList = new ArrayList<>();
            for (float value : values) {
                valuesList.add(value);
            }
            return new NotIn(key, valuesList);
        }

        public MetadataFilter nin(double... values) {
            return new NotIn(key, stream(values).boxed().collect(toList()));
        }

        public MetadataFilter nin(Collection<?> values) {
            // TODO test all values of same type? not null? of supported types only?
            return new NotIn(key, values);
        }
    }
}