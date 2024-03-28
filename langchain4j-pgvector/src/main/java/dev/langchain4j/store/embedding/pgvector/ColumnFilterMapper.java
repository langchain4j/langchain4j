package dev.langchain4j.store.embedding.pgvector;

class ColumnFilterMapper extends PgVectorFilterMapper {

    String formatKey(String key, Class<?> valueType) {
        return String.format("%s::%s", key, SQL_TYPE_MAP.get(valueType));
    }

    String formatKeyAsString(String key) {
        return key;
    }

}
