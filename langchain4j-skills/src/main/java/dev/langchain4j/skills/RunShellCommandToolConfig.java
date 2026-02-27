package dev.langchain4j.skills;

import java.util.concurrent.ExecutorService;

public class RunShellCommandToolConfig {

    final String name;
    final String description;
    final String commandParameterName;
    final String commandParameterDescription;
    final String skillNameParameterName;
    final String skillNameParameterDescription;
    final String timeoutSecondsParameterName;
    final String timeoutSecondsParameterDescription;
    final Integer maxStdoutChars;
    final Integer maxStderrChars;
    final ExecutorService executorService;

    private RunShellCommandToolConfig(Builder builder) {
        this.name = builder.name;
        this.description = builder.description;
        this.commandParameterName = builder.commandParameterName;
        this.commandParameterDescription = builder.commandParameterDescription;
        this.skillNameParameterName = builder.skillNameParameterName;
        this.skillNameParameterDescription = builder.skillNameParameterDescription;
        this.timeoutSecondsParameterName = builder.timeoutSecondsParameterName;
        this.timeoutSecondsParameterDescription = builder.timeoutSecondsParameterDescription;
        this.maxStdoutChars = builder.maxStdoutChars;
        this.maxStderrChars = builder.maxStderrChars;
        this.executorService = builder.executorService;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private String name;
        private String description;
        private String commandParameterName;
        private String commandParameterDescription;
        private String skillNameParameterName;
        private String skillNameParameterDescription;
        private String timeoutSecondsParameterName;
        private String timeoutSecondsParameterDescription;
        private Integer maxStdoutChars;
        private Integer maxStderrChars;
        private ExecutorService executorService;

        /**
         * Sets the name of the {@code run_shell_command} tool.
         * <p>
         * Default value is {@value Skills#DEFAULT_RUN_SHELL_COMMAND_TOOL_NAME}.
         */
        public Builder name(String name) {
            this.name = name;
            return this;
        }

        /**
         * Sets the description of the {@code run_shell_command} tool.
         * <p>
         * By default, the description is generated dynamically and includes the current OS name.
         */
        public Builder description(String description) {
            this.description = description;
            return this;
        }

        /**
         * Sets the name of the {@code command} parameter of the {@code run_shell_command} tool.
         * <p>
         * Default value is {@value Skills#DEFAULT_RUN_SHELL_COMMAND_TOOL_COMMAND_PARAMETER_NAME}.
         */
        public Builder commandParameterName(String commandParameterName) {
            this.commandParameterName = commandParameterName;
            return this;
        }

        /**
         * Sets the description of the {@code command} parameter of the {@code run_shell_command} tool.
         * <p>
         * Default value is {@value Skills#DEFAULT_RUN_SHELL_COMMAND_TOOL_COMMAND_PARAMETER_DESCRIPTION}.
         */
        public Builder commandParameterDescription(String commandParameterDescription) {
            this.commandParameterDescription = commandParameterDescription;
            return this;
        }

        /**
         * Sets the name of the {@code skill_name} parameter of the {@code run_shell_command} tool.
         * <p>
         * Default value is {@value Skills#DEFAULT_RUN_SHELL_COMMAND_TOOL_SKILL_NAME_PARAMETER_NAME}.
         */
        public Builder skillNameParameterName(String skillNameParameterName) {
            this.skillNameParameterName = skillNameParameterName;
            return this;
        }

        /**
         * Sets the description of the {@code skill_name} parameter of the {@code run_shell_command} tool.
         * <p>
         * Default value is {@value Skills#DEFAULT_RUN_SHELL_COMMAND_TOOL_SKILL_NAME_PARAMETER_DESCRIPTION}.
         */
        public Builder skillNameParameterDescription(String skillNameParameterDescription) {
            this.skillNameParameterDescription = skillNameParameterDescription;
            return this;
        }

        /**
         * Sets the name of the {@code timeout_seconds} parameter of the {@code run_shell_command} tool.
         * <p>
         * Default value is {@value Skills#DEFAULT_RUN_SHELL_COMMAND_TOOL_TIMEOUT_SECONDS_PARAMETER_NAME}.
         */
        public Builder timeoutSecondsParameterName(String timeoutSecondsParameterName) {
            this.timeoutSecondsParameterName = timeoutSecondsParameterName;
            return this;
        }

        /**
         * Sets the description of the {@code timeout_seconds} parameter of the {@code run_shell_command} tool.
         * <p>
         * Default value is {@value Skills#DEFAULT_RUN_SHELL_COMMAND_TOOL_TIMEOUT_SECONDS_PARAMETER_DESCRIPTION}.
         */
        public Builder timeoutSecondsParameterDescription(String timeoutSecondsParameterDescription) {
            this.timeoutSecondsParameterDescription = timeoutSecondsParameterDescription;
            return this;
        }

        /**
         * Sets the maximum number of characters of stdout to include in the tool result returned to the LLM.
         * If the output exceeds this limit, the beginning is discarded and a truncation notice is prepended.
         * <p>
         * Default value is {@value Skills#DEFAULT_RUN_SHELL_COMMAND_TOOL_MAX_STDOUT_CHARS}.
         */
        public Builder maxStdoutChars(Integer maxStdoutChars) {
            this.maxStdoutChars = maxStdoutChars;
            return this;
        }

        /**
         * Sets the maximum number of characters of stderr to include in the tool result returned to the LLM.
         * If the output exceeds this limit, the beginning is discarded and a truncation notice is prepended.
         * <p>
         * Default value is {@value Skills#DEFAULT_RUN_SHELL_COMMAND_TOOL_MAX_STDERR_CHARS}.
         */
        public Builder maxStderrChars(Integer maxStderrChars) {
            this.maxStderrChars = maxStderrChars;
            return this;
        }

        /**
         * Sets the {@link ExecutorService} used to read the stdout and stderr streams
         * of shell commands submitted via the {@code run_shell_command} tool.
         * <p>
         * By default, {@link dev.langchain4j.internal.DefaultExecutorProvider#getDefaultExecutorService()} is used.
         */
        public Builder executorService(ExecutorService executorService) {
            this.executorService = executorService;
            return this;
        }

        public RunShellCommandToolConfig build() {
            return new RunShellCommandToolConfig(this);
        }
    }
}
