package dev.langchain4j.store.memory.chat;

public enum SQLDialect {

    POSTGRESQL {
        @Override
        public String createTableSql(String table, String memoryIdColumnName, String contentColumnName) {
            return "CREATE TABLE IF NOT EXISTS " + table + " ("
                    + memoryIdColumnName + " VARCHAR(255) PRIMARY KEY, "
                    + contentColumnName + " TEXT NOT NULL DEFAULT '')";
        }

        @Override
        public String upsertSql(String table, String memoryIdColumnName, String contentColumnName) {
            return "INSERT INTO " + table + " (" + memoryIdColumnName + ", " + contentColumnName + ") VALUES (?, ?) "
                    + "ON CONFLICT (" + memoryIdColumnName + ") DO UPDATE SET " + contentColumnName + " = EXCLUDED." + contentColumnName;
        }
    },

    MYSQL {
        @Override
        public String createTableSql(String table, String memoryIdColumnName, String contentColumnName) {
            return "CREATE TABLE IF NOT EXISTS " + table + " ("
                    + memoryIdColumnName + " VARCHAR(255) PRIMARY KEY, "
                    + contentColumnName + " LONGTEXT NOT NULL)";
        }

        @Override
        public String upsertSql(String table, String memoryIdColumnName, String contentColumnName) {
            return "INSERT INTO " + table + " (" + memoryIdColumnName + ", " + contentColumnName + ") VALUES (?, ?) "
                    + "ON DUPLICATE KEY UPDATE " + contentColumnName + " = VALUES(" + contentColumnName + ")";
        }
    },

    H2 {
        @Override
        public String createTableSql(String table, String memoryIdColumnName, String contentColumnName) {
            return "CREATE TABLE IF NOT EXISTS " + table + " ("
                    + memoryIdColumnName + " VARCHAR(255) PRIMARY KEY, "
                    + contentColumnName + " TEXT NOT NULL DEFAULT '')";
        }

        @Override
        public String upsertSql(String table, String memoryIdColumnName, String contentColumnName) {
            return "MERGE INTO " + table + " (" + memoryIdColumnName + ", " + contentColumnName + ") KEY(" + memoryIdColumnName + ") VALUES (?, ?)";
        }
    };

    public abstract String createTableSql(String table, String memoryIdColumnName, String contentColumnName);

    public abstract String upsertSql(String table, String memoryIdColumnName, String contentColumnName);
}
