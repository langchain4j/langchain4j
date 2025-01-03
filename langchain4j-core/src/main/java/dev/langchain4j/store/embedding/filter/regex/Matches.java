package dev.langchain4j.store.embedding.filter.regex;

import static dev.langchain4j.internal.Exceptions.illegalArgument;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.store.embedding.filter.Filter;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * A filter that checks if the entire value of a metadata key matches a regular expression {@link Pattern}.
 * The value of the metadata key must be a string or a UUID.
 * Uses {@link java.util.regex.Matcher#matches}.
 */
public class Matches implements Filter {

    private final String key;
    private final Pattern pattern;

    public Matches(String key, Pattern pattern) {
        this.key = ensureNotBlank(key, "key");
        this.pattern = ensureNotNull(pattern, "pattern with key '" + key + "'");
    }

    public String key() {
        return key;
    }

    public Pattern pattern() {
        return pattern;
    }

    @Override
    public boolean test(Object object) {
        if (!(object instanceof Metadata metadata)) {
            return false;
        }

        if (!metadata.containsKey(key)) {
            return false;
        }

        Object actualValue = metadata.toMap().get(key);

        if (actualValue == null) {
            return false;
        }

        if (actualValue instanceof UUID uuid) {
            return pattern.matcher(uuid.toString()).matches();
        }

        if (actualValue instanceof String str) {
            return pattern.matcher(str).matches();
        }

        throw illegalArgument(
                "Type mismatch: actual value of metadata key \"%s\" (%s) has type %s, "
                        + "while it is expected to be a string or a UUID",
                key, actualValue, actualValue.getClass().getName());
    }

    @Override
    public boolean equals(final Object o) {
        if (o == this) return true;
        if (!(o instanceof Matches other)) return false;

        return Objects.equals(this.key, other.key) && Objects.equals(this.pattern, other.pattern);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key, pattern);
    }

    @Override
    public String toString() {
        return "Matches(key=" + this.key + ", pattern=" + this.pattern + ")";
    }
}
