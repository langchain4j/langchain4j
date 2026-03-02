package dev.langchain4j.skills;

import java.util.concurrent.ExecutorService;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.skills.ShellCommandRunner.DEFAULT_TIMEOUT_SECONDS;

public class RunShellCommandToolConfig {

    static final String DEFAULT_NAME = "run_shell_command";
    static final String DEFAULT_DESCRIPTION = "Runs a shell command using " + System.getProperty("os.name") + ". If skill name is specified, the command runs in the skill's root directory.";
    static final String DEFAULT_COMMAND_PARAMETER_NAME = "command";
    static final String DEFAULT_COMMAND_PARAMETER_DESCRIPTION = "The shell command to execute";
    static final String DEFAULT_SKILL_NAME_PARAMETER_NAME = "skill_name";
    static final String DEFAULT_SKILL_NAME_PARAMETER_DESCRIPTION = "Optional. The name of the skill whose root directory will be used as the working directory.";
    static final String DEFAULT_TIMEOUT_SECONDS_PARAMETER_NAME = "timeout_seconds";
    static final String DEFAULT_TIMEOUT_SECONDS_PARAMETER_DESCRIPTION = "Optional. The command timeout in seconds. Default: %s seconds.".formatted(DEFAULT_TIMEOUT_SECONDS);
    static final int DEFAULT_MAX_STDOUT_CHARS = 10_000;
    static final int DEFAULT_MAX_STDERR_CHARS = 10_000;

    final String name;
    final String description;
    final String commandParameterName;
    final String commandParameterDescription;
    final String skillNameParameterName;
    final String skillNameParameterDescription;
    final String timeoutSecondsParameterName;
    final String timeoutSecondsParameterDescription;
    final int maxStdOutChars;
    final int maxStdErrChars;
    final ExecutorService executorService;

    private RunShellCommandToolConfig(Builder builder) {
        this.name = getOrDefault(builder.name, DEFAULT_NAME);
        this.description = getOrDefault(builder.description, DEFAULT_DESCRIPTION);
        this.commandParameterName = getOrDefault(builder.commandParameterName, DEFAULT_COMMAND_PARAMETER_NAME);
        this.commandParameterDescription = getOrDefault(builder.commandParameterDescription, DEFAULT_COMMAND_PARAMETER_DESCRIPTION);
        this.skillNameParameterName = getOrDefault(builder.skillNameParameterName, DEFAULT_SKILL_NAME_PARAMETER_NAME);
        this.skillNameParameterDescription = getOrDefault(builder.skillNameParameterDescription, DEFAULT_SKILL_NAME_PARAMETER_DESCRIPTION);
        this.timeoutSecondsParameterName = getOrDefault(builder.timeoutSecondsParameterName, DEFAULT_TIMEOUT_SECONDS_PARAMETER_NAME);
        this.timeoutSecondsParameterDescription = getOrDefault(builder.timeoutSecondsParameterDescription, DEFAULT_TIMEOUT_SECONDS_PARAMETER_DESCRIPTION);
        this.maxStdOutChars = getOrDefault(builder.maxStdOutChars, DEFAULT_MAX_STDOUT_CHARS);
        this.maxStdErrChars = getOrDefault(builder.maxStdErrChars, DEFAULT_MAX_STDERR_CHARS);
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
        private Integer maxStdOutChars;
        private Integer maxStdErrChars;
        private ExecutorService executorService;

        /**
         * Sets the name of the {@code run_shell_command} tool.
         * <p>
         * Default value is {@value RunShellCommandToolConfig#DEFAULT_NAME}.
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
         * Default value is {@value RunShellCommandToolConfig#DEFAULT_COMMAND_PARAMETER_NAME}.
         */
        public Builder commandParameterName(String commandParameterName) {
            this.commandParameterName = commandParameterName;
            return this;
        }

        /**
         * Sets the description of the {@code command} parameter of the {@code run_shell_command} tool.
         * <p>
         * Default value is {@value RunShellCommandToolConfig#DEFAULT_COMMAND_PARAMETER_DESCRIPTION}.
         */
        public Builder commandParameterDescription(String commandParameterDescription) {
            this.commandParameterDescription = commandParameterDescription;
            return this;
        }

        /**
         * Sets the name of the {@code skill_name} parameter of the {@code run_shell_command} tool.
         * <p>
         * Default value is {@value RunShellCommandToolConfig#DEFAULT_SKILL_NAME_PARAMETER_NAME}.
         */
        public Builder skillNameParameterName(String skillNameParameterName) {
            this.skillNameParameterName = skillNameParameterName;
            return this;
        }

        /**
         * Sets the description of the {@code skill_name} parameter of the {@code run_shell_command} tool.
         * <p>
         * Default value is {@value RunShellCommandToolConfig#DEFAULT_SKILL_NAME_PARAMETER_DESCRIPTION}.
         */
        public Builder skillNameParameterDescription(String skillNameParameterDescription) {
            this.skillNameParameterDescription = skillNameParameterDescription;
            return this;
        }

        /**
         * Sets the name of the {@code timeout_seconds} parameter of the {@code run_shell_command} tool.
         * <p>
         * Default value is {@value RunShellCommandToolConfig#DEFAULT_TIMEOUT_SECONDS_PARAMETER_NAME}.
         */
        public Builder timeoutSecondsParameterName(String timeoutSecondsParameterName) {
            this.timeoutSecondsParameterName = timeoutSecondsParameterName;
            return this;
        }

        /**
         * Sets the description of the {@code timeout_seconds} parameter of the {@code run_shell_command} tool.
         * <p>
         * Default value is {@value RunShellCommandToolConfig#DEFAULT_TIMEOUT_SECONDS_PARAMETER_NAME}.
         */
        public Builder timeoutSecondsParameterDescription(String timeoutSecondsParameterDescription) {
            this.timeoutSecondsParameterDescription = timeoutSecondsParameterDescription;
            return this;
        }

        /**
         * Sets the maximum number of characters of stdout to include in the tool result returned to the LLM.
         * If the output exceeds this limit, the beginning is discarded and a truncation notice is prepended.
         * <p>
         * Default value is {@value RunShellCommandToolConfig#DEFAULT_MAX_STDOUT_CHARS}.
         */
        public Builder maxStdOutChars(Integer maxStdOutChars) {
            this.maxStdOutChars = maxStdOutChars;
            return this;
        }

        /**
         * Sets the maximum number of characters of stderr to include in the tool result returned to the LLM.
         * If the output exceeds this limit, the beginning is discarded and a truncation notice is prepended.
         * <p>
         * Default value is {@value RunShellCommandToolConfig#DEFAULT_MAX_STDERR_CHARS}.
         */
        public Builder maxStdErrChars(Integer maxStdErrChars) {
            this.maxStdErrChars = maxStdErrChars;
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
