package dev.langchain4j.agent.tool;

/**
 * TODO
 */
public class ToolResult {

    private final String result;

    public ToolResult(String result) {
        this.result = result; // TODO nullable?
    }

    public String result() {
        return result;
    }
}
