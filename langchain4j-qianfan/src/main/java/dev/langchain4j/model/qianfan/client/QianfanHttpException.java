package dev.langchain4j.model.qianfan.client;

public class QianfanHttpException extends RuntimeException {
    private final int code;

    public QianfanHttpException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int code() {
        return this.code;
    }
}
