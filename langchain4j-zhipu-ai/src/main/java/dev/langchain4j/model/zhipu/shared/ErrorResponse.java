package dev.langchain4j.model.zhipu.shared;

import java.util.Map;

public class ErrorResponse {
    private Map<String, String> error;

    public Map<String, String> getError() {
        return error;
    }

    public void setError(Map<String, String> error) {
        this.error = error;
    }
}
