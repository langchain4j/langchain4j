package dev.langchain4j.model.zhipu;

import dev.langchain4j.model.zhipu.shared.ErrorResponse;
import okhttp3.ResponseBody;

import java.io.IOException;

public class ZhipuAiException extends RuntimeException {

    /**
     * error code see <a href="https://open.bigmodel.cn/dev/api#error-code-v3">error codes document</a>
     */
    private String code;
    private String message;

    public ZhipuAiException(String code, String message) {
        super(message);
        this.code = code;
    }

    public ZhipuAiException(okhttp3.Response response) {
        try {
            ResponseBody body = response.body();
            if (body != null) {
                ErrorResponse errorResponse = Json.fromJson(body.string(), ErrorResponse.class);
                this.message = errorResponse.getError().get("message");
                this.code = errorResponse.getError().get("code");
            }
        } catch (IOException ignored) {
            this.message = "null";
            this.code = String.valueOf(response.code());
        }
    }

    public ZhipuAiException(String message) {
        super(message);
        this.message = message;
    }

    public String getCode() {
        return code;
    }
}
