package dev.langchain4j.model.dashscope.extension.common;

public enum Role {

    /**
     * The human side.
     */
    USER("user"),
    ASSISTANT("assistant"),
    /**
     * The model side.
     */
    BOT("bot"),
    SYSTEM("system"),
    ATTACHMENT("attachment"),
    TOOL("tool");

    private final String value;

    private Role(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
