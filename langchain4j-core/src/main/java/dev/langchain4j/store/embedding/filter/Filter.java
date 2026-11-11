package dev.langchain4j.store.embedding.filter;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.internal.JacocoIgnoreCoverageGenerated;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.comparison.ContainsString;
import dev.langchain4j.store.embedding.filter.comparison.IsEqualTo;
import dev.langchain4j.store.embedding.filter.comparison.IsGreaterThan;
import dev.langchain4j.store.embedding.filter.comparison.IsGreaterThanOrEqualTo;
import dev.langchain4j.store.embedding.filter.comparison.IsIn;
import dev.langchain4j.store.embedding.filter.comparison.IsLessThan;
import dev.langchain4j.store.embedding.filter.comparison.IsLessThanOrEqualTo;
import dev.langchain4j.store.embedding.filter.comparison.IsNotEqualTo;
import dev.langchain4j.store.embedding.filter.comparison.IsNotIn;
import dev.langchain4j.store.embedding.filter.logical.And;
import dev.langchain4j.store.embedding.filter.logical.Not;
import dev.langchain4j.store.embedding.filter.logical.Or;

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
 * <p>
 * The value that a comparison filter ({@link IsEqualTo}, {@link IsNotEqualTo}, {@link IsGreaterThan},
 * {@link IsGreaterThanOrEqualTo}, {@link IsLessThan}, {@link IsLessThanOrEqualTo}, {@link IsIn} or
 * {@link IsNotIn}) compares with must be an instance of exactly one of these classes:
 * {@code String}, {@code UUID}, {@code Boolean}, {@code Integer}, {@code Long}, {@code Float}, {@code Double},
 * {@code Byte}, {@code Short}, {@code BigInteger}, {@code BigDecimal}, {@code LocalDate}, {@code LocalDateTime},
 * {@code LocalTime}, {@code OffsetDateTime}, {@code OffsetTime}, {@code ZonedDateTime}, {@code Instant}, {@code Year},
 * {@code YearMonth}, {@code MonthDay}, {@code java.util.Date}, {@code java.sql.Date}, {@code java.sql.Time} or
 * {@code java.sql.Timestamp}, or it must be an enum.
 * Subclasses, for example of {@code BigDecimal}, are not accepted, and a {@code Float} or {@code Double} must be
 * a finite number (not {@code NaN} or infinite). For {@link IsIn} and {@link IsNotIn}, this applies to every
 * value in the collection. Any other value is rejected with an {@link IllegalArgumentException} when the filter
 * is created. Convert such a value first, for example a {@code Character} with {@code String.valueOf()}.
 * <p>
 * {@link Metadata} itself can only store {@code String}, {@code UUID}, {@code Integer}, {@code Long},
 * {@code Float} and {@code Double} values, so compare with the same type as the stored value,
 * for example an enum by its {@code name()}.
 * The other types are meant for embedding stores that hold data written outside LangChain4j,
 * and are passed to the embedding store as they are.
 * Individual embedding stores may support only some of these types.
 * For {@link IsGreaterThan}, {@link IsGreaterThanOrEqualTo}, {@link IsLessThan} and {@link IsLessThanOrEqualTo},
 * use numbers, {@code String}s or dates and times: embedding stores order {@code UUID}, {@code Boolean} and enum
 * values differently.
 * <p>
 * Stored metadata values can be {@code Float} or {@code Double}, and those can be infinite or {@code NaN}.
 * Infinite values are compared as one would expect: {@code -Infinity} is smaller and {@code +Infinity} is
 * greater than any other number. {@code NaN} is not comparable to anything, not even to itself, so every
 * comparison with it is {@code false}: a {@code NaN} metadata value satisfies neither
 * {@code IsEqualTo("key", 0.5)} nor {@code IsGreaterThan("key", 0)}, and only {@link IsNotEqualTo} and
 * {@link IsNotIn} match it.
 *
 * @see IsEqualTo
 * @see IsNotEqualTo
 * @see IsGreaterThan
 * @see IsGreaterThanOrEqualTo
 * @see IsLessThan
 * @see IsLessThanOrEqualTo
 * @see IsIn
 * @see IsNotIn
 * @see ContainsString
 * @see And
 * @see Not
 * @see Or
 */
@JacocoIgnoreCoverageGenerated
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
}
