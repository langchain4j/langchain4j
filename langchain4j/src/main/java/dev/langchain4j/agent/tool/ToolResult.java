package dev.langchain4j.agent.tool;

import dev.langchain4j.Experimental;

/**
 * TODO
 */
@Experimental
public class ToolResult {

    private final String result; // TODO name resultString?

    public ToolResult(String result) {
        this.result = result; // TODO nullable?
    }

    public String result() { // TODO name
        return result;
    }

    public static ToolResult from(String result) {
        return new ToolResult(result);
    }
}
