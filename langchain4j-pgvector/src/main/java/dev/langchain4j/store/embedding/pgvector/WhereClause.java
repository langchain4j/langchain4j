package dev.langchain4j.store.embedding.pgvector;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;

/**
 * A SQL condition with {@code ?} placeholders and the values to bind to them, in order.
 */
record WhereClause(String sql, List<String> parameters) {

    static final WhereClause EMPTY = new WhereClause("", List.of());

    /**
     * Binds the parameters as untyped values, so PostgreSQL infers their types from the SQL,
     * as it does for literals.
     *
     * @return the index after the last bound parameter
     */
    int bindParameters(PreparedStatement statement, int firstIndex) throws SQLException {
        int index = firstIndex;
        for (String parameter : parameters) {
            statement.setObject(index++, parameter, Types.OTHER);
        }
        return index;
    }
}
