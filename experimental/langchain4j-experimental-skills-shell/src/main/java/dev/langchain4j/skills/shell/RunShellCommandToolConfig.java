package dev.langchain4j.skills.shell;

import dev.langchain4j.Experimental;
import dev.langchain4j.exception.ToolArgumentsException;
import dev.langchain4j.exception.ToolExecutionException;
import dev.langchain4j.internal.DefaultExecutorProvider;

import java.nio.file.Path;
import java.util.concurrent.ExecutorService;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.skills.shell.ShellCommandRunner.DEFAULT_TIMEOUT_SECONDS;

@Experimental
public class RunShellCommandToolConfig {

    static final String DEFAULT_NAME = "run_shell_command";
    static final String DEFAULT_DESCRIPTION = "Runs a shell command using " + System.getProperty("os.name");
    static final String DEFAULT_COMMAND_PARAMETER_NAME = "command";
    static final String DEFAULT_COMMAND_PARAMETER_DESCRIPTION = "The shell command to execute";
    static final String DEFAULT_TIMEOUT_SECONDS_PARAMETER_NAME = "timeout_seconds";
    static final String DEFAULT_TIMEOUT_SECONDS_PARAMETER_DESCRIPTION = "Optional. The command timeout in seconds. Default: 30 seconds";
    static final int DEFAULT_MAX_STDOUT_CHARS = 10_000;
    static final int DEFAULT_MAX_STDERR_CHARS = 10_000;

    final String name;
    final String description;
    final String commandParameterName;
    final String commandParameterDescription;
    final String timeoutSecondsParameterName;
    final String timeoutSecondsParameterDescription;
    final int maxStdOutChars;
    final int maxStdErrChars;
    final Path workingDirectory;
    final ExecutorService executorService;
    final boolean throwToolArgumentsExceptions;

    private RunShellCommandToolConfig(Builder builder) {
        this.name = getOrDefault(builder.name, DEFAULT_NAME);
        this.description = getOrDefault(builder.description, DEFAULT_DESCRIPTION);
        this.commandParameterName = getOrDefault(builder.commandParameterName, DEFAULT_COMMAND_PARAMETER_NAME);
        this.commandParameterDescription = getOrDefault(builder.commandParameterDescription, DEFAULT_COMMAND_PARAMETER_DESCRIPTION);
        this.timeoutSecondsParameterName = getOrDefault(builder.timeoutSecondsParameterName, DEFAULT_TIMEOUT_SECONDS_PARAMETER_NAME);
        this.timeoutSecondsParameterDescription = getOrDefault(builder.timeoutSecondsParameterDescription, DEFAULT_TIMEOUT_SECONDS_PARAMETER_DESCRIPTION);
        this.maxStdOutChars = getOrDefault(builder.maxStdOutChars, DEFAULT_MAX_STDOUT_CHARS);
        this.maxStdErrChars = getOrDefault(builder.maxStdErrChars, DEFAULT_MAX_STDERR_CHARS);
        this.workingDirectory = getOrDefault(builder.workingDirectory, () -> Path.of(System.getProperty("user.dir")));
        this.executorService = getOrDefault(builder.executorService, DefaultExecutorProvider::getDefaultExecutorService);
        this.throwToolArgumentsExceptions = getOrDefault(builder.throwToolArgumentsExceptions, false);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private String name;
        private String description;
        private String commandParameterName;
        private String commandParameterDescription;
        private String timeoutSecondsParameterName;
        private String timeoutSecondsParameterDescription;
        private Integer maxStdOutChars;
        private Integer maxStdErrChars;
        private Path workingDirectory;
        private ExecutorService executorService;
        private Boolean throwToolArgumentsExceptions;

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
         * Default value is {@value RunShellCommandToolConfig#DEFAULT_TIMEOUT_SECONDS_PARAMETER_DESCRIPTION}.
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
         * Sets the working directory in which shell commands will be executed.
         * <p>
         * By default, the current JVM working directory ({@code user.dir}) is used.
         */
        public Builder workingDirectory(Path workingDirectory) {
            this.workingDirectory = workingDirectory;
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

        /**
         * Controls which exception type is thrown when tool arguments
         * are missing, invalid, or cannot be parsed.
         * <p>
         * Although all errors produced by this tool are argument-related,
         * this strategy throws {@link ToolExecutionException} by default
         * instead of {@link ToolArgumentsException}.
         * <p>
         * The reason is historical: by default, AI Services fail fast when
         * a {@link ToolArgumentsException} is thrown, whereas
         * {@link ToolExecutionException} allows the error message to be
         * returned to the LLM. For these tools, returning the error message
         * to the LLM is usually the desired behavior.
         * <p>
         * If this flag is set to {@code true}, {@link ToolArgumentsException}
         * will be thrown instead.
         *
         * @param throwToolArgumentsExceptions whether to throw {@link ToolArgumentsException}
         * @return this builder
         */
        public Builder throwToolArgumentsExceptions(Boolean throwToolArgumentsExceptions) {
            this.throwToolArgumentsExceptions = throwToolArgumentsExceptions;
            return this;
        }

        public RunShellCommandToolConfig build() {
            return new RunShellCommandToolConfig(this);
        }
    }
}
