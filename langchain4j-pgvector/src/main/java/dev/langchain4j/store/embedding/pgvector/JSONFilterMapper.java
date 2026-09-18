package dev.langchain4j.store.embedding.pgvector;

class JSONFilterMapper extends PgVectorFilterMapper {
    final String metadataColumn;

    public JSONFilterMapper(String metadataColumn) {
        this.metadataColumn = metadataColumn;
    }

    String formatKey(String key, Class<?> valueType) {
        return String.format("(%s->>'%s')::%s", metadataColumn, escapeKey(key), SQL_TYPE_MAP.get(valueType));
    }

    String formatKeyAsString(String key) {
        return metadataColumn + "->>'" + escapeKey(key) + "'";
    }

    /**
     * The key is embedded into a single-quoted SQL string literal (the JSON key of the {@code ->>}
     * operator). Double the single quotes so a crafted key cannot break out of the literal and
     * inject SQL. PostgreSQL defaults to {@code standard_conforming_strings = on}, so backslash is a
     * literal character and only the single quote needs escaping.
     */
    private static String escapeKey(String key) {
        return key.replace("'", "''");
    }
}
