package dev.langchain4j.store.embedding.filter.builder.sql;

import dev.langchain4j.Experimental;

/**
 * Defines the retry strategy to use when SQL parsing fails in {@link LanguageModelSqlFilterBuilder}.
 * <br>
 * These strategies determine how the system behaves when the language model generates
 * invalid or unparseable SQL.
 */
@Experimental
public enum RetryStrategy {

    /**
     * No retry - if parsing fails, return null (no filter).
     * This is the default behavior for backward compatibility.
     */
    NONE,

    /**
     * Feed the error message back to the language model and ask it to fix the SQL.
     * This strategy can help recover from minor syntax errors.
     */
    RETRY_WITH_ERROR_FEEDBACK,

    /**
     * Simplify the query by removing complex parts and retry.
     * This can help when the LLM generates overly complex SQL.
     */
    RETRY_SIMPLIFIED,

    /**
     * Use an alternative prompt template that emphasizes simpler SQL generation.
     * This is useful when the default prompt leads to complex or invalid SQL.
     */
    RETRY_WITH_SIMPLE_PROMPT
}
