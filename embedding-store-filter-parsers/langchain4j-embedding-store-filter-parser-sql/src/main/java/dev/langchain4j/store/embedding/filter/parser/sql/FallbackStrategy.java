package dev.langchain4j.store.embedding.filter.parser.sql;

import dev.langchain4j.Experimental;

/**
 * Defines the strategy to use when SQL parsing fails.
 * <br>
 * This allows configuring the behavior of {@link SqlFilterParser} when it encounters
 * an unsupported SQL expression or parsing error.
 */
@Experimental
public enum FallbackStrategy {

    /**
     * Throws an exception when parsing fails.
     * This is useful when strict validation is required.
     */
    FAIL,

    /**
     * Returns {@code null} when parsing fails, which means no filtering will be applied.
     * This is the default behavior for backward compatibility.
     */
    IGNORE,

    /**
     * Attempts to parse as much as possible and returns a partial filter
     * containing only the successfully parsed portions.
     * This is useful when dealing with complex SQL that may contain unsupported features.
     */
    PARTIAL
}
