package dev.langchain4j.agentskills.execution;

import dev.langchain4j.Experimental;

/**
 * Result of a script execution.
 *
 * @author Shrink (shunke.wjl@alibaba-inc.com)
 * @since 1.12.0
 */
@Experimental
public class ScriptExecutionResult {

    private final int exitCode;
    private final String output;
    private final String error;

    private ScriptExecutionResult(Builder builder) {
        this.exitCode = builder.exitCode;
        this.output = builder.output != null ? builder.output : "";
        this.error = builder.error != null ? builder.error : "";
    }

    /**
     * Returns the exit code of the script execution.
     *
     * @return the exit code (0 typically means success)
     */
    public int exitCode() {
        return exitCode;
    }

    /**
     * Returns the standard output from the script.
     *
     * @return the output, never null
     */
    public String output() {
        return output;
    }

    /**
     * Returns the standard error from the script.
     *
     * @return the error output, never null
     */
    public String error() {
        return error;
    }

    /**
     * Returns true if the script executed successfully (exit code 0).
     *
     * @return true if successful
     */
    public boolean isSuccess() {
        return exitCode == 0;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private int exitCode;
        private String output;
        private String error;

        public Builder exitCode(int exitCode) {
            this.exitCode = exitCode;
            return this;
        }

        public Builder output(String output) {
            this.output = output;
            return this;
        }

        public Builder error(String error) {
            this.error = error;
            return this;
        }

        public ScriptExecutionResult build() {
            return new ScriptExecutionResult(this);
        }
    }
}
