package dev.langchain4j.skills;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.service.tool.ToolExecutionResult;
import dev.langchain4j.service.tool.ToolExecutor;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static dev.langchain4j.skills.Skills.getArgument;
import static dev.langchain4j.skills.Skills.parseArguments;
import static dev.langchain4j.skills.Skills.throwException;

class RunShellCommandToolExecutor implements ToolExecutor {

    private final Map<String, Skill> skillsByName;
    private final String commandParameterName;
    private final String skillNameParameterName;
    private final String timeoutSecondsParameterName;
    private final ExecutorService executorService;
    private final boolean throwToolArgumentsExceptions;

    public RunShellCommandToolExecutor(Map<String, Skill> skillsByName,
                                       String commandParameterName,
                                       String skillNameParameterName,
                                       String timeoutSecondsParameterName,
                                       ExecutorService executorService,
                                       boolean throwToolArgumentsExceptions) {
        this.skillsByName = copy(skillsByName);
        this.commandParameterName = ensureNotBlank(commandParameterName, "commandParameterName");
        this.skillNameParameterName = ensureNotBlank(skillNameParameterName, "skillNameParameterName");
        this.timeoutSecondsParameterName = ensureNotBlank(timeoutSecondsParameterName, "timeoutSecondsParameterName");
        this.executorService = ensureNotNull(executorService, "executorService");
        this.throwToolArgumentsExceptions = throwToolArgumentsExceptions;
    }

    @Override
    public ToolExecutionResult executeWithContext(ToolExecutionRequest request, InvocationContext context) {

        Map<String, Object> arguments = parseArguments(request.arguments(), throwToolArgumentsExceptions);
        String command = getArgument(commandParameterName, arguments, throwToolArgumentsExceptions);
        String skillName = arguments.containsKey(skillNameParameterName) ? arguments.get(skillNameParameterName).toString() : null;
        Integer timeoutSeconds = getTimeoutSeconds(arguments);

        Path workingDir = null;
        if (skillName != null && !skillName.isBlank()) {
            Skill skill = skillsByName.get(skillName);
            if (skill == null) {
                throwException("There is no skill with name '%s'".formatted(skillName), throwToolArgumentsExceptions);
            }
            if (skill instanceof FileSystemSkill fileSystemSkill) {
                workingDir = fileSystemSkill.basePath();
            }
        }

        // Resolve the effective CWD so it can be included in every result.
        // The LLM needs to know the absolute path to construct reliable paths
        // across commands, since each invocation starts a fresh shell process.
        Path resolvedCwd = workingDir != null
                ? workingDir.toAbsolutePath()
                : Path.of(System.getProperty("user.dir"));
        String cwdHeader = "Working directory: " + resolvedCwd + "\n"; // TODO opt-in

        try {
            ShellCommandRunner.Result runResult = ShellCommandRunner.run(command, workingDir, timeoutSeconds, executorService);
            // TODO truncate output to N chars
            if (runResult.isSuccess()) {
                String output = runResult.stdOut().isBlank() ? "(no output)" : runResult.stdOut();
                return ToolExecutionResult.builder()
                        .resultText(cwdHeader + "Output: " + output) // TODO truncate, configurable
                        .build();
            } else {
                String errorText = cwdHeader
                        + "Exit code: " + runResult.exitCode() + "\n"
                        + "STDOUT:\n" + (runResult.stdOut().isEmpty() ? "(empty)" : runResult.stdOut()) + "\n"
                        + "STDERR:\n" + (runResult.stdErr().isEmpty() ? "(empty)" : runResult.stdErr());
                return ToolExecutionResult.builder()
                        .isError(true)
                        .resultText(errorText)
                        .build();
            }
        } catch (ShellCommandRunner.TimeoutException e) {
            return ToolExecutionResult.builder()
                    .isError(true)
                    .resultText(e.getMessage() + "\n\nSTDOUT: " + e.partialStdOut() + "\n\nSTDERR: " + e.partialStdErr())
                    .build();
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            return ToolExecutionResult.builder()
                    .isError(true)
                    .resultText("Failed to run command: " + e.getMessage())
                    .build();
        }
    }

    Integer getTimeoutSeconds(Map<String, Object> arguments) {
        Object timeoutSeconds = arguments.get(timeoutSecondsParameterName);
        if (timeoutSeconds == null) {
            return null;
        }
        if (timeoutSeconds instanceof Integer i) {
            return i;
        }
        return Integer.valueOf(timeoutSeconds.toString());
    }

    @Override
    public String execute(ToolExecutionRequest request, Object memoryId) {
        throw new IllegalStateException("executeWithContext must be called instead");
    }
}
