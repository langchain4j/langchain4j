package dev.langchain4j.store.embedding.clickhouse;

public class ClickhouseOperationException extends RuntimeException {

    public ClickhouseOperationException(String msg) {
        super(msg);
    }
}
