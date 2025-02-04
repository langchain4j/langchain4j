package dev.langchain4j.store.embedding.mariadb;

import java.sql.SQLException;
import org.mariadb.jdbc.Driver;

class ColumnFilterMapper extends MariaDbFilterMapper {

    String formatKey(String key) {
        try {
            return Driver.enquoteIdentifier(key, true);
        } catch (SQLException e) {
            return key;
        }
    }
}
