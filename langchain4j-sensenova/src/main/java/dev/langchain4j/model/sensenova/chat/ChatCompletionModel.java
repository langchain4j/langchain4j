package dev.langchain4j.model.sensenova.chat;

public enum ChatCompletionModel {
    SENSE_CHAT_5("SenseChat-5"),
    SENSE_CHAT("SenseChat"),
    SENSECHAT_32K("SenseChat-32K"),
    SENSECHAT_128K("SenseChat-128K"),
    SENSECHAT_TURBO("SenseChat-Turbo"),
    SENSECHAT_FUNCTIONCALL("SenseChat-FunctionCall"),
    ;

    private final String value;

    ChatCompletionModel(String value) {
        this.value = value;
    }

    @Override
    public String toString() {
        return this.value;
    }
}
