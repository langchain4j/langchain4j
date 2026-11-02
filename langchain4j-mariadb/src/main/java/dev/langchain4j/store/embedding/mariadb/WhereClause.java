package dev.langchain4j.store.embedding.mariadb;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

/**
 * A SQL condition with {@code ?} placeholders and the values to bind to them, in order.
 */
record WhereClause(String sql, List<Object> parameters) {

    static final WhereClause EMPTY = new WhereClause("", List.of());

    /**
     * @return the index after the last bound parameter
     */
    int bindParameters(PreparedStatement statement, int firstIndex) throws SQLException {
        int index = firstIndex;
        for (Object parameter : parameters) {
            statement.setObject(index++, parameter);
        }
        return index;
    }
}
