package dev.langchain4j.store.embedding.oceanbase.sql;

import dev.langchain4j.store.embedding.filter.Filter;

/**
 * Transforms a metadata {@link Filter} into a SQL expression that can be used in a WHERE clause.
 */
public interface SQLFilter {

    /**
     * Returns a SQL expression that can be used in a WHERE clause to filter results based on metadata values.
     * The returned SQL expression should evaluate to true when a row meets the criteria of the metadata filter,
     * and false otherwise.
     *
     * @return A SQL expression, or null if this filter is equivalent to "always include" all rows.
     */
    String toSql();

    /**
     * Returns true if the SQL filter is guaranteed to match all rows, or false otherwise.
     * When a SQL filter matches all rows (e.g., as if there were no filter), the result of
     * {@link #toSql()} may be null or an expression which always evaluates to true, such as "1=1".
     * <p>
     * When {@link #toSql()} is null, the SQL filter MUST match all rows, and this method MUST return true.
     *
     * @return True if this SQL filter matches all rows, or false if it might exclude some rows.
     */
    boolean matchesAllRows();

    /**
     * Returns true if this SQL filter is guaranteed to match no rows. When a SQL filter matches no rows,
     * the result of {@link #toSql()} should be an expression which always evaluates to false, such as "1=0".
     *
     * @return True if this SQL filter matches no rows, or false if it might include some rows.
     */
    boolean matchesNoRows();
}
