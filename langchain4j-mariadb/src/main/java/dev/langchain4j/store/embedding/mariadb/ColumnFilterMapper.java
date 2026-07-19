package dev.langchain4j.store.embedding.mariadb;

class ColumnFilterMapper extends MariaDbFilterMapper {

    String formatKey(String key) {
        return MariaDbValidator.validateAndEnquoteIdentifier(key, true);
    }
}
