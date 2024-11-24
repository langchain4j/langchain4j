package dev.langchain4j.store.embedding.duckdb;

public class DuckDBSQLException extends RuntimeException{
    public DuckDBSQLException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
