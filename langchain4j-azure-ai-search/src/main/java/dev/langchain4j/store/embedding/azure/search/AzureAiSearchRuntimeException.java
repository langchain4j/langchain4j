package dev.langchain4j.store.embedding.azure.search;

import dev.langchain4j.exception.LangChain4jException;

public class AzureAiSearchRuntimeException extends LangChain4jException {

    public AzureAiSearchRuntimeException(String message) {
        super(message);
    }

    public AzureAiSearchRuntimeException(String message, Throwable cause) {
        super(message, cause);
    }
}
