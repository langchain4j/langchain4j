package dev.langchain4j.store.embedding.oceanbase;

import dev.langchain4j.exception.LangChain4jException;

class RequestToOceanBaseFailedException extends LangChain4jException {

    public RequestToOceanBaseFailedException(String message) {
        super(message);
    }

    public RequestToOceanBaseFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}

