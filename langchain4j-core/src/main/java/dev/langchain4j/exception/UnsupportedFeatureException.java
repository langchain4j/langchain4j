package dev.langchain4j.exception;

import dev.langchain4j.Experimental;

@Experimental
public class UnsupportedFeatureException extends LangChain4jException {

    public UnsupportedFeatureException(String message) {
        super(message);
    }
}
