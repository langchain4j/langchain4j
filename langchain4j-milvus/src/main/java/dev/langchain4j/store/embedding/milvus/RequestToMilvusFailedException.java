package dev.langchain4j.store.embedding.milvus;

import dev.langchain4j.exception.LangChain4jException;

class RequestToMilvusFailedException extends LangChain4jException {

    public RequestToMilvusFailedException(String message) {
        super(message);
    }

    public RequestToMilvusFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
