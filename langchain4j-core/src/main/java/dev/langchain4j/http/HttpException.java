package dev.langchain4j.http;

import dev.langchain4j.Experimental;

@Experimental
public class HttpException extends RuntimeException {

    private final Integer statusCode;

    public HttpException(Integer statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }

    /**
     * HTTP response status code. Can be {@code null}.
     */
    public Integer statusCode() {
        return statusCode;
    }
}
