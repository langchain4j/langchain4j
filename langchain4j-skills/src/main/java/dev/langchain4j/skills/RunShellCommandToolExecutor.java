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
import static dev.langchain4j.internal.ValidationUtils.ensureGreaterThanZero;
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
    private final int maxStdoutChars;
    private final int maxStderrChars;

    public RunShellCommandToolExecutor(Map<String, Skill> skillsByName,
                                       String commandParameterName,
                                       String skillNameParameterName,
                                       String timeoutSecondsParameterName,
                                       ExecutorService executorService,
                                       boolean throwToolArgumentsExceptions,
                                       int maxStdoutChars,
                                       int maxStderrChars) {
        this.skillsByName = copy(skillsByName);
        this.commandParameterName = ensureNotBlank(commandParameterName, "commandParameterName");
        this.skillNameParameterName = ensureNotBlank(skillNameParameterName, "skillNameParameterName");
        this.timeoutSecondsParameterName = ensureNotBlank(timeoutSecondsParameterName, "timeoutSecondsParameterName");
        this.executorService = ensureNotNull(executorService, "executorService");
        this.throwToolArgumentsExceptions = throwToolArgumentsExceptions;
        this.maxStdoutChars = ensureGreaterThanZero(maxStdoutChars, "maxStdoutChars");
        this.maxStderrChars = ensureGreaterThanZero(maxStderrChars, "maxStderrChars");
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
            if (runResult.isSuccess()) {
                String output = runResult.stdOut().isBlank() ? "(empty)" : truncate(runResult.stdOut(), maxStdoutChars);
                return ToolExecutionResult.builder()
                        .resultText(cwdHeader + "Output: " + output)
                        .build();
            } else {
                String stdout = runResult.stdOut().isEmpty() ? "(empty)" : truncate(runResult.stdOut(), maxStdoutChars);
                String stderr = runResult.stdErr().isEmpty() ? "(empty)" : truncate(runResult.stdErr(), maxStderrChars);
                String errorText = cwdHeader
                        + "Exit code: " + runResult.exitCode() + "\n"
                        + "STDOUT:\n" + stdout + "\n"
                        + "STDERR:\n" + stderr;
                return ToolExecutionResult.builder()
                        .isError(true)
                        .resultText(errorText)
                        .build();
            }
        } catch (ShellCommandRunner.TimeoutException e) {
            return ToolExecutionResult.builder()
                    .isError(true)
                    .resultText(e.getMessage() + "\n\nSTDOUT: " + truncate(e.partialStdOut(), maxStdoutChars) + "\n\nSTDERR: " + truncate(e.partialStdErr(), maxStderrChars))
                    .build();
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            return ToolExecutionResult.builder()
                    .isError(true)
                    .resultText("Failed to run command: " + e.getMessage())
                    .build();
        }
    }

    private static String truncate(String text, int maxChars) {
        if (text.length() <= maxChars) return text;
        return "[truncated: showing last " + maxChars + " of " + text.length() + " chars]\n"
                + text.substring(text.length() - maxChars);
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
