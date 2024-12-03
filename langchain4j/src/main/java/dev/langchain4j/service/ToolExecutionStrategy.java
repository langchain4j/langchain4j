package dev.langchain4j.service;

import dev.langchain4j.agent.tool.ToolExecutionRequest;

public enum ToolExecutionStrategy {

    /**
     * Returns {@link ToolExecutionRequest} without executing the tool.
     */
    RETURN_TOOL_EXECUTION_REQUEST,

    /**
     * Executes tool and returns the LLM-generated text response.
     */
    EXECUTE_TOOL
}
