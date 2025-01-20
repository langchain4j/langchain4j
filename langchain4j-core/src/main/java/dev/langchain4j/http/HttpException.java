package dev.langchain4j.http;

import dev.langchain4j.Experimental;

@Experimental
public class HttpException extends RuntimeException {

    // TODO retry logic in core should be aware of this exception? move http stuff to core? extract retry into separate module?

    private final int statusCode;

    public HttpException(int statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }

    public int statusCode() {
        return statusCode;
    }
}
