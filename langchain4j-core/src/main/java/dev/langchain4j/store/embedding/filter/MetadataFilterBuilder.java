package dev.langchain4j.store.embedding.filter;

import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static java.util.Arrays.asList;
import static java.util.Arrays.stream;
import static java.util.stream.Collectors.toList;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.internal.JacocoIgnoreCoverageGenerated;
import dev.langchain4j.store.embedding.filter.comparison.ContainsString;
import dev.langchain4j.store.embedding.filter.comparison.IsEqualTo;
import dev.langchain4j.store.embedding.filter.comparison.IsGreaterThan;
import dev.langchain4j.store.embedding.filter.comparison.IsGreaterThanOrEqualTo;
import dev.langchain4j.store.embedding.filter.comparison.IsIn;
import dev.langchain4j.store.embedding.filter.comparison.IsLessThan;
import dev.langchain4j.store.embedding.filter.comparison.IsLessThanOrEqualTo;
import dev.langchain4j.store.embedding.filter.comparison.IsNotEqualTo;
import dev.langchain4j.store.embedding.filter.comparison.IsNotIn;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * A helper class for building a {@link Filter} for {@link Metadata} key.
 */
@JacocoIgnoreCoverageGenerated
public class MetadataFilterBuilder {

    private final String key;

    public MetadataFilterBuilder(String key) {
        this.key = ensureNotBlank(key, "key");
    }

    /**
     * Creates a {@link MetadataFilterBuilder} for the given metadata key.
     *
     * @param key the metadata key name
     * @return a new {@link MetadataFilterBuilder} for the given key
     */
    public static MetadataFilterBuilder metadataKey(String key) {
        return new MetadataFilterBuilder(key);
    }

    // containsString

    /**
     * Creates a {@link Filter} that matches documents whose metadata value for this key contains the given substring.
     *
     * @param value the substring to search for
     * @return the {@link Filter}
     */
    public Filter containsString(String value) {
        return new ContainsString(key, value);
    }

    // isEqualTo

    /**
     * Creates a {@link Filter} that matches documents whose metadata value for this key equals the given value.
     *
     * @param value the value to compare against
     * @return the {@link Filter}
     */
    public Filter isEqualTo(String value) {
        return new IsEqualTo(key, value);
    }

    /**
     * Creates a {@link Filter} that matches documents whose metadata value for this key equals the given UUID.
     *
     * @param value the UUID to compare against
     * @return the {@link Filter}
     */
    public Filter isEqualTo(UUID value) {
        return new IsEqualTo(key, value);
    }

    /**
     * Creates a {@link Filter} that matches documents whose metadata value for this key equals the given int.
     *
     * @param value the int to compare against
     * @return the {@link Filter}
     */
    public Filter isEqualTo(int value) {
        return new IsEqualTo(key, value);
    }

    /**
     * Creates a {@link Filter} that matches documents whose metadata value for this key equals the given long.
     *
     * @param value the long to compare against
     * @return the {@link Filter}
     */
    public Filter isEqualTo(long value) {
        return new IsEqualTo(key, value);
    }

    /**
     * Creates a {@link Filter} that matches documents whose metadata value for this key equals the given float.
     *
     * @param value the float to compare against
     * @return the {@link Filter}
     */
    public Filter isEqualTo(float value) {
        return new IsEqualTo(key, value);
    }

    /**
     * Creates a {@link Filter} that matches documents whose metadata value for this key equals the given double.
     *
     * @param value the double to compare against
     * @return the {@link Filter}
     */
    public Filter isEqualTo(double value) {
        return new IsEqualTo(key, value);
    }

    // isNotEqualTo

    /**
     * Creates a {@link Filter} that matches documents whose metadata value for this key does not equal the given value.
     *
     * @param value the value to compare against
     * @return the {@link Filter}
     */
    public Filter isNotEqualTo(String value) {
        return new IsNotEqualTo(key, value);
    }

    /**
     * Creates a {@link Filter} that matches documents whose metadata value for this key does not equal the given UUID.
     *
     * @param value the UUID to compare against
     * @return the {@link Filter}
     */
    public Filter isNotEqualTo(UUID value) {
        return new IsNotEqualTo(key, value);
    }

    /**
     * Creates a {@link Filter} that matches documents whose metadata value for this key does not equal the given int.
     *
     * @param value the int to compare against
     * @return the {@link Filter}
     */
    public Filter isNotEqualTo(int value) {
        return new IsNotEqualTo(key, value);
    }

    /**
     * Creates a {@link Filter} that matches documents whose metadata value for this key does not equal the given long.
     *
     * @param value the long to compare against
     * @return the {@link Filter}
     */
    public Filter isNotEqualTo(long value) {
        return new IsNotEqualTo(key, value);
    }

    /**
     * Creates a {@link Filter} that matches documents whose metadata value for this key does not equal the given float.
     *
     * @param value the float to compare against
     * @return the {@link Filter}
     */
    public Filter isNotEqualTo(float value) {
        return new IsNotEqualTo(key, value);
    }

    /**
     * Creates a {@link Filter} that matches documents whose metadata value for this key does not equal the given double.
     *
     * @param value the double to compare against
     * @return the {@link Filter}
     */
    public Filter isNotEqualTo(double value) {
        return new IsNotEqualTo(key, value);
    }

    // isGreaterThan

    /**
     * Creates a {@link Filter} that matches documents whose metadata value for this key is greater than the given value.
     *
     * @param value the value to compare against
     * @return the {@link Filter}
     */
    public Filter isGreaterThan(String value) {
        return new IsGreaterThan(key, value);
    }

    /**
     * Creates a {@link Filter} that matches documents whose metadata value for this key is greater than the given int.
     *
     * @param value the int to compare against
     * @return the {@link Filter}
     */
    public Filter isGreaterThan(int value) {
        return new IsGreaterThan(key, value);
    }

    /**
     * Creates a {@link Filter} that matches documents whose metadata value for this key is greater than the given long.
     *
     * @param value the long to compare against
     * @return the {@link Filter}
     */
    public Filter isGreaterThan(long value) {
        return new IsGreaterThan(key, value);
    }

    /**
     * Creates a {@link Filter} that matches documents whose metadata value for this key is greater than the given float.
     *
     * @param value the float to compare against
     * @return the {@link Filter}
     */
    public Filter isGreaterThan(float value) {
        return new IsGreaterThan(key, value);
    }

    /**
     * Creates a {@link Filter} that matches documents whose metadata value for this key is greater than the given double.
     *
     * @param value the double to compare against
     * @return the {@link Filter}
     */
    public Filter isGreaterThan(double value) {
        return new IsGreaterThan(key, value);
    }

    // isGreaterThanOrEqualTo

    /**
     * Creates a {@link Filter} that matches documents whose metadata value for this key is greater than or equal to
     * the given value.
     *
     * @param value the value to compare against
     * @return the {@link Filter}
     */
    public Filter isGreaterThanOrEqualTo(String value) {
        return new IsGreaterThanOrEqualTo(key, value);
    }

    /**
     * Creates a {@link Filter} that matches documents whose metadata value for this key is greater than or equal to
     * the given int.
     *
     * @param value the int to compare against
     * @return the {@link Filter}
     */
    public Filter isGreaterThanOrEqualTo(int value) {
        return new IsGreaterThanOrEqualTo(key, value);
    }

    /**
     * Creates a {@link Filter} that matches documents whose metadata value for this key is greater than or equal to
     * the given long.
     *
     * @param value the long to compare against
     * @return the {@link Filter}
     */
    public Filter isGreaterThanOrEqualTo(long value) {
        return new IsGreaterThanOrEqualTo(key, value);
    }

    /**
     * Creates a {@link Filter} that matches documents whose metadata value for this key is greater than or equal to
     * the given float.
     *
     * @param value the float to compare against
     * @return the {@link Filter}
     */
    public Filter isGreaterThanOrEqualTo(float value) {
        return new IsGreaterThanOrEqualTo(key, value);
    }

    /**
     * Creates a {@link Filter} that matches documents whose metadata value for this key is greater than or equal to
     * the given double.
     *
     * @param value the double to compare against
     * @return the {@link Filter}
     */
    public Filter isGreaterThanOrEqualTo(double value) {
        return new IsGreaterThanOrEqualTo(key, value);
    }

    // isLessThan

    /**
     * Creates a {@link Filter} that matches documents whose metadata value for this key is less than the given value.
     *
     * @param value the value to compare against
     * @return the {@link Filter}
     */
    public Filter isLessThan(String value) {
        return new IsLessThan(key, value);
    }

    /**
     * Creates a {@link Filter} that matches documents whose metadata value for this key is less than the given int.
     *
     * @param value the int to compare against
     * @return the {@link Filter}
     */
    public Filter isLessThan(int value) {
        return new IsLessThan(key, value);
    }

    /**
     * Creates a {@link Filter} that matches documents whose metadata value for this key is less than the given long.
     *
     * @param value the long to compare against
     * @return the {@link Filter}
     */
    public Filter isLessThan(long value) {
        return new IsLessThan(key, value);
    }

    /**
     * Creates a {@link Filter} that matches documents whose metadata value for this key is less than the given float.
     *
     * @param value the float to compare against
     * @return the {@link Filter}
     */
    public Filter isLessThan(float value) {
        return new IsLessThan(key, value);
    }

    /**
     * Creates a {@link Filter} that matches documents whose metadata value for this key is less than the given double.
     *
     * @param value the double to compare against
     * @return the {@link Filter}
     */
    public Filter isLessThan(double value) {
        return new IsLessThan(key, value);
    }

    // isLessThanOrEqualTo

    /**
     * Creates a {@link Filter} that matches documents whose metadata value for this key is less than or equal to
     * the given value.
     *
     * @param value the value to compare against
     * @return the {@link Filter}
     */
    public Filter isLessThanOrEqualTo(String value) {
        return new IsLessThanOrEqualTo(key, value);
    }

    /**
     * Creates a {@link Filter} that matches documents whose metadata value for this key is less than or equal to
     * the given int.
     *
     * @param value the int to compare against
     * @return the {@link Filter}
     */
    public Filter isLessThanOrEqualTo(int value) {
        return new IsLessThanOrEqualTo(key, value);
    }

    /**
     * Creates a {@link Filter} that matches documents whose metadata value for this key is less than or equal to
     * the given long.
     *
     * @param value the long to compare against
     * @return the {@link Filter}
     */
    public Filter isLessThanOrEqualTo(long value) {
        return new IsLessThanOrEqualTo(key, value);
    }

    /**
     * Creates a {@link Filter} that matches documents whose metadata value for this key is less than or equal to
     * the given float.
     *
     * @param value the float to compare against
     * @return the {@link Filter}
     */
    public Filter isLessThanOrEqualTo(float value) {
        return new IsLessThanOrEqualTo(key, value);
    }

    /**
     * Creates a {@link Filter} that matches documents whose metadata value for this key is less than or equal to
     * the given double.
     *
     * @param value the double to compare against
     * @return the {@link Filter}
     */
    public Filter isLessThanOrEqualTo(double value) {
        return new IsLessThanOrEqualTo(key, value);
    }

    // isBetween

    /**
     * Creates a {@link Filter} that matches documents whose metadata value for this key is between
     * {@code fromValue} and {@code toValue}, inclusive.
     *
     * @param fromValue the lower bound (inclusive)
     * @param toValue   the upper bound (inclusive)
     * @return the {@link Filter}
     */
    public Filter isBetween(String fromValue, String toValue) {
        return isGreaterThanOrEqualTo(fromValue).and(isLessThanOrEqualTo(toValue));
    }

    /**
     * Creates a {@link Filter} that matches documents whose metadata value for this key is between
     * {@code fromValue} and {@code toValue}, inclusive.
     *
     * @param fromValue the lower bound (inclusive)
     * @param toValue   the upper bound (inclusive)
     * @return the {@link Filter}
     */
    public Filter isBetween(int fromValue, int toValue) {
        return isGreaterThanOrEqualTo(fromValue).and(isLessThanOrEqualTo(toValue));
    }

    /**
     * Creates a {@link Filter} that matches documents whose metadata value for this key is between
     * {@code fromValue} and {@code toValue}, inclusive.
     *
     * @param fromValue the lower bound (inclusive)
     * @param toValue   the upper bound (inclusive)
     * @return the {@link Filter}
     */
    public Filter isBetween(long fromValue, long toValue) {
        return isGreaterThanOrEqualTo(fromValue).and(isLessThanOrEqualTo(toValue));
    }

    /**
     * Creates a {@link Filter} that matches documents whose metadata value for this key is between
     * {@code fromValue} and {@code toValue}, inclusive.
     *
     * @param fromValue the lower bound (inclusive)
     * @param toValue   the upper bound (inclusive)
     * @return the {@link Filter}
     */
    public Filter isBetween(float fromValue, float toValue) {
        return isGreaterThanOrEqualTo(fromValue).and(isLessThanOrEqualTo(toValue));
    }

    /**
     * Creates a {@link Filter} that matches documents whose metadata value for this key is between
     * {@code fromValue} and {@code toValue}, inclusive.
     *
     * @param fromValue the lower bound (inclusive)
     * @param toValue   the upper bound (inclusive)
     * @return the {@link Filter}
     */
    public Filter isBetween(double fromValue, double toValue) {
        return isGreaterThanOrEqualTo(fromValue).and(isLessThanOrEqualTo(toValue));
    }

    // isIn

    /**
     * Creates a {@link Filter} that matches documents whose metadata value for this key is one of the given strings.
     *
     * @param values the allowed values
     * @return the {@link Filter}
     */
    public Filter isIn(String... values) {
        return new IsIn(key, asList(values));
    }

    /**
     * Creates a {@link Filter} that matches documents whose metadata value for this key is one of the given UUIDs.
     *
     * @param values the allowed UUIDs
     * @return the {@link Filter}
     */
    public Filter isIn(UUID... values) {
        return new IsIn(key, asList(values));
    }

    /**
     * Creates a {@link Filter} that matches documents whose metadata value for this key is one of the given ints.
     *
     * @param values the allowed int values
     * @return the {@link Filter}
     */
    public Filter isIn(int... values) {
        return new IsIn(key, stream(values).boxed().collect(toList()));
    }

    /**
     * Creates a {@link Filter} that matches documents whose metadata value for this key is one of the given longs.
     *
     * @param values the allowed long values
     * @return the {@link Filter}
     */
    public Filter isIn(long... values) {
        return new IsIn(key, stream(values).boxed().collect(toList()));
    }

    /**
     * Creates a {@link Filter} that matches documents whose metadata value for this key is one of the given floats.
     *
     * @param values the allowed float values
     * @return the {@link Filter}
     */
    public Filter isIn(float... values) {
        List<Float> valuesList = new ArrayList<>();
        for (float value : values) {
            valuesList.add(value);
        }
        return new IsIn(key, valuesList);
    }

    /**
     * Creates a {@link Filter} that matches documents whose metadata value for this key is one of the given doubles.
     *
     * @param values the allowed double values
     * @return the {@link Filter}
     */
    public Filter isIn(double... values) {
        return new IsIn(key, stream(values).boxed().collect(toList()));
    }

    /**
     * Creates a {@link Filter} that matches documents whose metadata value for this key is one of the given values.
     *
     * @param values the collection of allowed values
     * @return the {@link Filter}
     */
    public Filter isIn(Collection<?> values) {
        return new IsIn(key, values);
    }

    // isNotIn

    /**
     * Creates a {@link Filter} that matches documents whose metadata value for this key is none of the given strings.
     *
     * @param values the disallowed values
     * @return the {@link Filter}
     */
    public Filter isNotIn(String... values) {
        return new IsNotIn(key, asList(values));
    }

    /**
     * Creates a {@link Filter} that matches documents whose metadata value for this key is none of the given UUIDs.
     *
     * @param values the disallowed UUIDs
     * @return the {@link Filter}
     */
    public Filter isNotIn(UUID... values) {
        return new IsNotIn(key, asList(values));
    }

    /**
     * Creates a {@link Filter} that matches documents whose metadata value for this key is none of the given ints.
     *
     * @param values the disallowed int values
     * @return the {@link Filter}
     */
    public Filter isNotIn(int... values) {
        return new IsNotIn(key, stream(values).boxed().collect(toList()));
    }

    /**
     * Creates a {@link Filter} that matches documents whose metadata value for this key is none of the given longs.
     *
     * @param values the disallowed long values
     * @return the {@link Filter}
     */
    public Filter isNotIn(long... values) {
        return new IsNotIn(key, stream(values).boxed().collect(toList()));
    }

    /**
     * Creates a {@link Filter} that matches documents whose metadata value for this key is none of the given floats.
     *
     * @param values the disallowed float values
     * @return the {@link Filter}
     */
    public Filter isNotIn(float... values) {
        List<Float> valuesList = new ArrayList<>();
        for (float value : values) {
            valuesList.add(value);
        }
        return new IsNotIn(key, valuesList);
    }

    /**
     * Creates a {@link Filter} that matches documents whose metadata value for this key is none of the given doubles.
     *
     * @param values the disallowed double values
     * @return the {@link Filter}
     */
    public Filter isNotIn(double... values) {
        return new IsNotIn(key, stream(values).boxed().collect(toList()));
    }

    /**
     * Creates a {@link Filter} that matches documents whose metadata value for this key is none of the given values.
     *
     * @param values the collection of disallowed values
     * @return the {@link Filter}
     */
    public Filter isNotIn(Collection<?> values) {
        return new IsNotIn(key, values);
    }
}
