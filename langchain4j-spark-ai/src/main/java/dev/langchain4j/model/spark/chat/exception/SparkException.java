package dev.langchain4j.model.spark.chat.exception;


import dev.langchain4j.model.spark.chat.constant.SparkResponseErrorCode;
import lombok.Data;

@Data
public class SparkException extends RuntimeException {

    private Integer code;

    private String sid;

    public SparkException(Integer code, String message) {
        super(message);
        this.code = code;
    }

    public SparkException(Integer code, String message, Throwable t) {
        super(message, t);
        this.code = code;
    }

    public static SparkException bizFailed(Integer code) {
        String errorMessage = SparkResponseErrorCode.RESPONSE_ERROR_MAP.get(code);
        if (null == errorMessage) {
            errorMessage = "未知的错误码";
        }
        return new SparkException(code, errorMessage);
    }
}
