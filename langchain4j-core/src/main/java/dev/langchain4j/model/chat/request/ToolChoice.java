package dev.langchain4j.model.chat.request;

import dev.langchain4j.model.chat.ChatModel;

/**
 * Specifies how {@link ChatModel} should use tools.
 */
public enum ToolChoice {

    /**
     * The chat model can choose whether to use tools, which ones to use, and how many.
     */
    AUTO,

    /**
     * The chat model is required to use one or more tools.
     */
    REQUIRED
}
