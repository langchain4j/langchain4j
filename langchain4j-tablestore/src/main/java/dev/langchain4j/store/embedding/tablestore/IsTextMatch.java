package dev.langchain4j.store.embedding.tablestore;

import dev.langchain4j.store.embedding.filter.Filter;

import java.util.Objects;
import java.util.StringJoiner;

import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

public class IsTextMatch implements Filter {

    private final String key;
    private final String comparisonValue;

    public IsTextMatch(String key, String comparisonValue) {
        this.key = ensureNotBlank(key, "key");
        this.comparisonValue = ensureNotNull(comparisonValue, "comparisonValue with key '" + key + "'");
    }

    public String key() {
        return key;
    }

    public String comparisonValue() {
        return comparisonValue;
    }

    @Override
    public boolean test(Object object) {
        throw new UnsupportedOperationException("only used in search filters");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof IsTextMatch)) {
            return false;
        }
        IsTextMatch that = (IsTextMatch) o;
        return Objects.equals(key, that.key) && Objects.equals(comparisonValue, that.comparisonValue);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key, comparisonValue);
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", IsTextMatch.class.getSimpleName() + "[", "]")
                .add("key='" + key + "'")
                .add("comparisonValue='" + comparisonValue + "'")
                .toString();
    }
}
