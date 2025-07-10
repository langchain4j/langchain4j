package dev.langchain4j.exception;

public class ContentFilteredException extends InvalidRequestException {

    private String contentFilterResults = "";
    private Throwable cause;

    public ContentFilteredException(final String message, final String contentFilterResults) {
        super(message);
        this.contentFilterResults = contentFilterResults;
    }

    public ContentFilteredException(final String message, final Throwable cause) {
        super(message);
        this.cause = cause;
    }

    public String getContentFilterResults() {
        return this.contentFilterResults;
    }

    public Throwable getCause() {
        return this.cause;
    }
}
