package dev.langchain4j.store.embedding.azure.search;

public class AzureAiSearchRuntimeException extends RuntimeException {

    public AzureAiSearchRuntimeException() {
        super();
    }

    public AzureAiSearchRuntimeException(String message) {
        super(message);
    }

    public AzureAiSearchRuntimeException(String message, Throwable cause) {
        super(message, cause);
    }
}
