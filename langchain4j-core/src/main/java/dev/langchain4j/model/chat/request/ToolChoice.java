package dev.langchain4j.model.chat.request;

import dev.langchain4j.Experimental;

@Experimental
public enum ToolChoice {

    // TODO improve javadoc

    /**
     * The language model is free to decide whether to call one or multiple tools.
     */
    AUTO,

    /**
     * The language model is required to call one or more tools.
     */
    REQUIRED
}
