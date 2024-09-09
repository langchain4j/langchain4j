package dev.langchain4j.store.embedding.clickhouse;

public class ClickHouseOperationException extends RuntimeException {

    public ClickHouseOperationException(String msg) {
        super(msg);
    }
}
