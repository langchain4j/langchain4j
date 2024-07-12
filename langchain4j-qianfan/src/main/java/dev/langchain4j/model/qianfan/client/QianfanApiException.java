package dev.langchain4j.model.qianfan.client;

public class QianfanApiException extends RuntimeException {
    private final int code;

    public QianfanApiException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int code() {
        return this.code;
    }
}
