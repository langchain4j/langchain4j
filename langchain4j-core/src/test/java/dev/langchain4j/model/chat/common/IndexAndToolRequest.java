package dev.langchain4j.model.chat.common;

import dev.langchain4j.agent.tool.ToolExecutionRequest;

public record IndexAndToolRequest(int index, ToolExecutionRequest toolRequest) {
}
