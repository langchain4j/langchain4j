package dev.langchain4j.store.embedding.opensearch;

public class OpenSearchRequestFailedException extends RuntimeException {

    public OpenSearchRequestFailedException() {
        super();
    }

    public OpenSearchRequestFailedException(String message) {
        super(message);
    }

    public OpenSearchRequestFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
