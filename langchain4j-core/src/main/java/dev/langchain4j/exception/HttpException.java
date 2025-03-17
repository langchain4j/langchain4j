package dev.langchain4j.exception;

import dev.langchain4j.Experimental;

@Experimental
public class HttpException extends RuntimeException {

    private final int statusCode;

    public HttpException(int statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }

    public int statusCode() {
        return statusCode;
    }
}
