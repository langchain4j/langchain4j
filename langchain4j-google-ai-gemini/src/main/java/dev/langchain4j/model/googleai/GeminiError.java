package dev.langchain4j.model.googleai;

import lombok.Data;

@Data
class GeminiError {
    private final Integer code;
    private final String message;
    private final String status;

    GeminiError(Integer code, String message, String status) {
        this.code = code;
        this.message = message;
        this.status = status;
    }
}
