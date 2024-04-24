package dev.langchain4j.model.zhipu;

import com.zhipu.oapi.Constants;

public enum ZhipuAiChatModelName {

    GLM_4(Constants.ModelChatGLM4),
    GLM_3_TURBO(Constants.ModelChatGLM3TURBO),
    ;


    private final String stringValue;

    ZhipuAiChatModelName(String stringValue) {
        this.stringValue = stringValue;
    }

    @Override
    public String toString() {
        return stringValue;
    }
}
