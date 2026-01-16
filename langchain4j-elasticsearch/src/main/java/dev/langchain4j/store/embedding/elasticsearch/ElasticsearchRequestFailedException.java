package dev.langchain4j.store.embedding.elasticsearch;

import dev.langchain4j.exception.LangChain4jException;

public class ElasticsearchRequestFailedException extends LangChain4jException {

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
