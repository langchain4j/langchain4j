package dev.langchain4j.model.azure;

public class AzureOpenAiRuntimeException extends RuntimeException {

    public AzureOpenAiRuntimeException() {
        super();
    }

    public AzureOpenAiRuntimeException(String message) {
        super(message);
    }

    public AzureOpenAiRuntimeException(String message, Throwable cause) {
        super(message, cause);
    }
}