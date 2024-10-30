package dev.langchain4j.code;

import org.jspecify.annotations.NonNull;

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
    @NonNull String execute(@NonNull String code);
}
