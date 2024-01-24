package dev.langchain4j.code;

/**
 * Interface for executing code.
 */
public interface CodeExecutionEngine {

    /**
     * Execute the given code.
     *
     * @param code The code to execute.
     * @return The result of the execution.
     */
    String execute(String code);
}
