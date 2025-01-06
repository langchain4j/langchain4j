package dev.langchain4j.exception;

import dev.langchain4j.Experimental;

@Experimental
public class UnsupportedFeatureException extends RuntimeException {

    public UnsupportedFeatureException(String message) {
        super(message);
    }
}
