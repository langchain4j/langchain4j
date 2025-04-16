package dev.langchain4j.model.chat.request;

public enum ToolChoice {

    // TODO improve javadoc

    /**
     * The chat model is free to decide whether to call tool(s).
     */
    AUTO,

    /**
     * The chat model is required to call one or more tools.
     */
    REQUIRED
}
