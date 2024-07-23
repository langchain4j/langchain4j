package dev.langchain4j.store.embedding.filter;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.store.embedding.filter.comparison.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static java.util.Arrays.asList;
import static java.util.Arrays.stream;
import static java.util.stream.Collectors.toList;

/**
 * A helper class for building a {@link Filter} for {@link Metadata} key.
 */
public class MetadataFilterBuilder {

    private final String key;

    public MetadataFilterBuilder(String key) {
        this.key = ensureNotBlank(key, "key");
    }

    public static MetadataFilterBuilder metadataKey(String key) {
        return new MetadataFilterBuilder(key);
    }


    // isEqualTo

    public Filter isEqualTo(String value) {
        return new IsEqualTo(key, value);
    }

    public Filter isEqualTo(UUID value) {
        return new IsEqualTo(key, value);
    }

    public Filter isEqualTo(int value) {
        return new IsEqualTo(key, value);
    }

    public Filter isEqualTo(long value) {
        return new IsEqualTo(key, value);
    }

    public Filter isEqualTo(float value) {
        return new IsEqualTo(key, value);
    }

    public Filter isEqualTo(double value) {
        return new IsEqualTo(key, value);
    }


    // isNotEqualTo

    public Filter isNotEqualTo(String value) {
        return new IsNotEqualTo(key, value);
    }

    public Filter isNotEqualTo(UUID value) {
        return new IsNotEqualTo(key, value);
    }

    public Filter isNotEqualTo(int value) {
        return new IsNotEqualTo(key, value);
    }

    public Filter isNotEqualTo(long value) {
        return new IsNotEqualTo(key, value);
    }

    public Filter isNotEqualTo(float value) {
        return new IsNotEqualTo(key, value);
    }

    public Filter isNotEqualTo(double value) {
        return new IsNotEqualTo(key, value);
    }


    // isGreaterThan

    public Filter isGreaterThan(String value) {
        return new IsGreaterThan(key, value);
    }

    public Filter isGreaterThan(int value) {
        return new IsGreaterThan(key, value);
    }

    public Filter isGreaterThan(long value) {
        return new IsGreaterThan(key, value);
    }

    public Filter isGreaterThan(float value) {
        return new IsGreaterThan(key, value);
    }

    public Filter isGreaterThan(double value) {
        return new IsGreaterThan(key, value);
    }


    // isGreaterThanOrEqualTo

    public Filter isGreaterThanOrEqualTo(String value) {
        return new IsGreaterThanOrEqualTo(key, value);
    }

    public Filter isGreaterThanOrEqualTo(int value) {
        return new IsGreaterThanOrEqualTo(key, value);
    }

    public Filter isGreaterThanOrEqualTo(long value) {
        return new IsGreaterThanOrEqualTo(key, value);
    }

    public Filter isGreaterThanOrEqualTo(float value) {
        return new IsGreaterThanOrEqualTo(key, value);
    }

    public Filter isGreaterThanOrEqualTo(double value) {
        return new IsGreaterThanOrEqualTo(key, value);
    }


    // isLessThan

    public Filter isLessThan(String value) {
        return new IsLessThan(key, value);
    }

    public Filter isLessThan(int value) {
        return new IsLessThan(key, value);
    }

    public Filter isLessThan(long value) {
        return new IsLessThan(key, value);
    }

    public Filter isLessThan(float value) {
        return new IsLessThan(key, value);
    }

    public Filter isLessThan(double value) {
        return new IsLessThan(key, value);
    }


    // isLessThanOrEqualTo

    public Filter isLessThanOrEqualTo(String value) {
        return new IsLessThanOrEqualTo(key, value);
    }

    public Filter isLessThanOrEqualTo(int value) {
        return new IsLessThanOrEqualTo(key, value);
    }

    public Filter isLessThanOrEqualTo(long value) {
        return new IsLessThanOrEqualTo(key, value);
    }

    public Filter isLessThanOrEqualTo(float value) {
        return new IsLessThanOrEqualTo(key, value);
    }

    public Filter isLessThanOrEqualTo(double value) {
        return new IsLessThanOrEqualTo(key, value);
    }


    // isBetween

    public Filter isBetween(String fromValue, String toValue) {
        return isGreaterThanOrEqualTo(fromValue).and(isLessThanOrEqualTo(toValue));
    }

    public Filter isBetween(int fromValue, int toValue) {
        return isGreaterThanOrEqualTo(fromValue).and(isLessThanOrEqualTo(toValue));
    }

    public Filter isBetween(long fromValue, long toValue) {
        return isGreaterThanOrEqualTo(fromValue).and(isLessThanOrEqualTo(toValue));
    }

    public Filter isBetween(float fromValue, float toValue) {
        return isGreaterThanOrEqualTo(fromValue).and(isLessThanOrEqualTo(toValue));
    }

    public Filter isBetween(double fromValue, double toValue) {
        return isGreaterThanOrEqualTo(fromValue).and(isLessThanOrEqualTo(toValue));
    }


    // isIn

    public Filter isIn(String... values) {
        return new IsIn(key, asList(values));
    }

    public Filter isIn(UUID... values) {
        return new IsIn(key, asList(values));
    }

    public Filter isIn(int... values) {
        return new IsIn(key, stream(values).boxed().collect(toList()));
    }

    public Filter isIn(long... values) {
        return new IsIn(key, stream(values).boxed().collect(toList()));
    }

    public Filter isIn(float... values) {
        List<Float> valuesList = new ArrayList<>();
        for (float value : values) {
            valuesList.add(value);
        }
        return new IsIn(key, valuesList);
    }

    public Filter isIn(double... values) {
        return new IsIn(key, stream(values).boxed().collect(toList()));
    }

    public Filter isIn(Collection<?> values) {
        return new IsIn(key, values);
    }


    // isNotIn

    public Filter isNotIn(String... values) {
        return new IsNotIn(key, asList(values));
    }

    public Filter isNotIn(UUID... values) {
        return new IsNotIn(key, asList(values));
    }

    public Filter isNotIn(int... values) {
        return new IsNotIn(key, stream(values).boxed().collect(toList()));
    }

    public Filter isNotIn(long... values) {
        return new IsNotIn(key, stream(values).boxed().collect(toList()));
    }

    public Filter isNotIn(float... values) {
        List<Float> valuesList = new ArrayList<>();
        for (float value : values) {
            valuesList.add(value);
        }
        return new IsNotIn(key, valuesList);
    }

    public Filter isNotIn(double... values) {
        return new IsNotIn(key, stream(values).boxed().collect(toList()));
    }

    public Filter isNotIn(Collection<?> values) {
        return new IsNotIn(key, values);
    }
}
