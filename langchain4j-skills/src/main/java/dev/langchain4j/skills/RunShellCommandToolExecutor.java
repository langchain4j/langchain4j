package dev.langchain4j.skills;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.service.tool.ToolExecutionResult;
import dev.langchain4j.service.tool.ToolExecutor;
import dev.langchain4j.skills.ShellCommandRunner.Result;

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
    private final int maxStdOutChars;
    private final int maxStdErrChars;

    public RunShellCommandToolExecutor(Map<String, Skill> skillsByName,
                                       String commandParameterName,
                                       String skillNameParameterName,
                                       String timeoutSecondsParameterName,
                                       ExecutorService executorService,
                                       boolean throwToolArgumentsExceptions,
                                       int maxStdOutChars,
                                       int maxStdErrChars) {
        this.skillsByName = copy(skillsByName);
        this.commandParameterName = ensureNotBlank(commandParameterName, "commandParameterName");
        this.skillNameParameterName = ensureNotBlank(skillNameParameterName, "skillNameParameterName");
        this.timeoutSecondsParameterName = ensureNotBlank(timeoutSecondsParameterName, "timeoutSecondsParameterName");
        this.executorService = ensureNotNull(executorService, "executorService");
        this.throwToolArgumentsExceptions = throwToolArgumentsExceptions;
        this.maxStdOutChars = ensureGreaterThanZero(maxStdOutChars, "maxStdOutChars");
        this.maxStdErrChars = ensureGreaterThanZero(maxStdErrChars, "maxStdErrChars");
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
        Path resolvedCwd = workingDir != null ? workingDir.toAbsolutePath() : Path.of(System.getProperty("user.dir"));

        try {
            Result result = ShellCommandRunner.run(command, workingDir, timeoutSeconds, executorService);
            String stdOut = formatStdOut(result.stdOut());
            if (result.isSuccess()) {
                String resultText = """
                        <working_dir>%s</working_dir>
                        <stdout>%s</stdout>"
                        """.formatted(resolvedCwd, stdOut);
                return ToolExecutionResult.builder()
                        .resultText(resultText)
                        .build();
            } else {
                String stdErr = formatStdErr(result.stdErr());
                String resultText = """
                        <working_dir>%s</working_dir>
                        <exit_code>%s</exit_code>
                        <stdout>%s</stdout>"
                        <stderr>%s</stderr>"
                        """.formatted(resolvedCwd, result.exitCode(), stdOut, stdErr);
                return ToolExecutionResult.builder()
                        .isError(true)
                        .resultText(resultText)
                        .build();
            }
        } catch (ShellCommandRunner.TimeoutException e) {
            String resultText = """
                    <working_dir>%s</working_dir>
                    <std_out>%s</std_out>"
                    <std_err>%s</std_err>"
                    """.formatted(resolvedCwd, formatStdOut(e.partialStdOut()), formatStdErr(e.partialStdErr()));
            return ToolExecutionResult.builder()
                    .isError(true)
                    .resultText(resultText)
                    .build();
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            return ToolExecutionResult.builder()
                    .isError(true)
                    .resultText("Failed to run command: " + e.getMessage())
                    .build();
        }
    }

    private String formatStdOut(String stdOut) {
        return stdOut.isEmpty() ? "(empty)" : truncate(stdOut, maxStdOutChars);
    }

    private String formatStdErr(String stdErr) {
        return stdErr.isEmpty() ? "(empty)" : truncate(stdErr, maxStdErrChars);
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
