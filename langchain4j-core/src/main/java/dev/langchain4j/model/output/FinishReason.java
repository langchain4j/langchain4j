package dev.langchain4j.model.output;

public enum FinishReason {

    STOP,
    LENGTH,
    TOOL_EXECUTION,
    CONTENT_FILTER
}
