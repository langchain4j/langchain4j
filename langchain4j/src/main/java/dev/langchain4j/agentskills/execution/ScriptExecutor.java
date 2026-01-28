package dev.langchain4j.agentskills.execution;

import dev.langchain4j.Experimental;

import java.nio.file.Path;

/**
 * Interface for executing scripts within a skill.
 * <p>
 * Implementations should handle script execution in a safe manner,
 * considering security implications.
 *
 * @author Shrink (shunke.wjl@alibaba-inc.com)
 * @since 1.12.0
 */
@Experimental
public interface ScriptExecutor {

    /**
     * Executes a script command.
     *
     * @param workingDirectory the working directory for script execution
     * @param command          the command to execute
     * @return the execution result
     */
    ScriptExecutionResult execute(Path workingDirectory, String command);
}
