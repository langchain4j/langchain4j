package dev.langchain4j.model.zhipu.chat;

public enum ChatCompletionModel {
    GLM_4("glm-4"),
    GLM_3_TURBO("glm-3-turbo"),
    CHATGLM_TURBO("chatglm_turbo");

    private final String value;

    ChatCompletionModel(String value) {
        this.value = value;
    }

    @Override
    public String toString() {
        return this.value;
    }
}
