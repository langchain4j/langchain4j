package dev.langchain4j.store.embedding.azure.cosmos.nosql;

import dev.langchain4j.exception.LangChain4jException;

public class AzureCosmosDBNoSqlRuntimeException extends LangChain4jException {

    public AzureCosmosDBNoSqlRuntimeException(String message) {
        super(message);
    }

    public AzureCosmosDBNoSqlRuntimeException(String message, Throwable cause) {
        super(message, cause);
    }
}
