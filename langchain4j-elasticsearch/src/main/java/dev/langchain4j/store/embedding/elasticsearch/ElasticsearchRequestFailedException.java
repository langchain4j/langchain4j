package dev.langchain4j.store.embedding.elasticsearch;

public class ElasticsearchRequestFailedException extends RuntimeException {

    public ElasticsearchRequestFailedException() {
        super();
    }

    public ElasticsearchRequestFailedException(String message) {
        super(message);
    }

    public ElasticsearchRequestFailedException(String message, Throwable cause) {
        super(message, cause);
    }

    public ElasticsearchRequestFailedException(Throwable cause) {
        super(cause);
    }
}
