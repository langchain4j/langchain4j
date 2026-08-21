package dev.langchain4j.store.embedding.pgvector;

import static dev.langchain4j.internal.Utils.isNullOrBlank;

import java.util.regex.Pattern;

class ColumnFilterMapper extends PgVectorFilterMapper {

    /**
     * In COLUMN_PER_KEY mode the metadata key maps to a column name and is concatenated into the SQL
     * as a bare identifier. Restrict it to a plain identifier so it cannot be used for SQL injection.
     * The dollar sign is intentionally excluded to avoid any interaction with PostgreSQL
     * dollar-quoted string literals.
     */
    private static final Pattern SAFE_IDENTIFIER = Pattern.compile("[a-zA-Z_][a-zA-Z0-9_]*");

    String formatKey(String key, Class<?> valueType) {
        return String.format("%s::%s", validateIdentifier(key), SQL_TYPE_MAP.get(valueType));
    }

    String formatKeyAsString(String key) {
        return validateIdentifier(key);
    }

    private static String validateIdentifier(String key) {
        if (isNullOrBlank(key) || !SAFE_IDENTIFIER.matcher(key).matches()) {
            throw new IllegalArgumentException("Invalid metadata key: '" + key + "'");
        }
        return key;
    }
}
