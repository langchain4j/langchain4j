package dev.langchain4j.agent.tool;

public interface ToolExecutor {

    String execute(ToolExecutionRequest toolExecutionRequest);
}
