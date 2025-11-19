package dev.langchain4j.store.embedding.filter.comparison;

import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.store.embedding.filter.Filter;
import java.util.Objects;

/**
 * A filter that represents a SQL-like pattern match on a metadata key.
 * <p>
 * This class is a descriptor and does not implement actual matching logic,
 * because each {@link dev.langchain4j.store.embedding.EmbeddingStore}
 * implementations handle LIKE/ILIKE differently.
 */
public class Like implements Filter {

    /**
     * Supported LIKE operators.
     */
    public enum Operator {
        LIKE,
        ILIKE,
        RLIKE,
        REGEXP
    }

    private final String key;
    private final String pattern;
    private final Operator operator;
    private final Boolean negated;

    /**
     * Main constructor with explicit likeKeyword.
     */
    public Like(String key, Object pattern, Operator operator, boolean negated) {
        this.key = ensureNotBlank(key, "key");
        this.pattern =
                ensureNotNull(pattern, "comparisonValue with key '" + key + "'").toString();
        this.operator = ensureNotNull(operator, "operator");
        this.negated = negated;
    }

    /**
     * Minimum constructor for like-operator concept
     * Allows subclasses like ContainsString to keep functioning as-is with super(key, comparisonValue).
     */
    protected Like(String key, Object comparisonValue) {
        this(key, comparisonValue, Operator.LIKE, false);
    }

    public String key() {
        return key;
    }

    public String pattern() {
        return pattern;
    }

    public Operator operator() {
        return operator;
    }

    public Boolean negated() {
        return negated;
    }

    /**
     * Test method is not implemented because actual matching depends on the underlying EmbeddingStore.
     */
    @Override
    public boolean test(Object object) {
        throw new UnsupportedOperationException(
                "Direct evaluation is not supported for Like. Matching is store-specific.");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Like other)) return false;

        return Objects.equals(this.key, other.key)
                && Objects.equals(this.pattern, other.pattern)
                && Objects.equals(this.operator, other.operator)
                && Objects.equals(this.negated, other.negated);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key, pattern, operator, negated);
    }

    @Override
    public String toString() {
        return "Like(column=" + key + ", pattern=" + pattern + ", like-operator=" + operator + ", negated=" + negated
                + ")";
    }
}
