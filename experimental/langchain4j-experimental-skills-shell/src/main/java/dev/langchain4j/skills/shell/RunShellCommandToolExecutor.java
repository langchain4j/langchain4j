package dev.langchain4j.skills.shell;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.exception.ToolArgumentsException;
import dev.langchain4j.exception.ToolExecutionException;
import dev.langchain4j.internal.Json;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.service.tool.ToolExecutionResult;
import dev.langchain4j.service.tool.ToolExecutor;
import dev.langchain4j.skills.shell.ShellCommandRunner.Result;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

class RunShellCommandToolExecutor implements ToolExecutor {

    private final RunShellCommandToolConfig config;

    RunShellCommandToolExecutor(RunShellCommandToolConfig config) {
        this.config = ensureNotNull(config, "config");
    }

    @Override
    public ToolExecutionResult executeWithContext(ToolExecutionRequest request, InvocationContext context) {

        Map<String, Object> arguments = parseArguments(request.arguments());
        String command = getRequiredArgument(config.commandParameterName, arguments);
        Path workingDir = config.workingDirectory;
        Integer timeoutSeconds = resolveTimeout(arguments);

        try {
            Result result = ShellCommandRunner.run(command, workingDir, timeoutSeconds, config.executorService);

            String stdOut = formatStdOut(result.stdOut());
            if (result.isSuccess()) {
                String resultText;
                if (result.stdErr().isBlank()) {
                    resultText = """
                            <working_dir>%s</working_dir>
                            <stdout>%s</stdout>""".formatted(workingDir, stdOut);
                } else {
                    String stdErr = formatStdErr(result.stdErr());
                    resultText = """
                            <working_dir>%s</working_dir>
                            <stdout>%s</stdout>
                            <stderr>%s</stderr>""".formatted(workingDir, stdOut, stdErr);
                }
                return ToolExecutionResult.builder()
                        .resultText(resultText)
                        .build();
            } else {
                String stdErr = formatStdErr(result.stdErr());
                String resultText = """
                        <working_dir>%s</working_dir>
                        <exit_code>%s</exit_code>
                        <stdout>%s</stdout>
                        <stderr>%s</stderr>""".formatted(workingDir, result.exitCode(), stdOut, stdErr);
                return ToolExecutionResult.builder()
                        .isError(true)
                        .resultText(resultText)
                        .build();
            }
        } catch (ShellCommandRunner.TimeoutException e) {
            String stdOut = formatStdOut(e.partialStdOut());
            String stdErr = formatStdErr(e.partialStdErr());
            String resultText = """
                    <working_dir>%s</working_dir>
                    <error>%s</error>
                    <stdout>%s</stdout>
                    <stderr>%s</stderr>""".formatted(workingDir, e.getMessage(), stdOut, stdErr);
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

    private Map<String, Object> parseArguments(String json) {
        try {
            return Json.fromJson(json, Map.class);
        } catch (Exception e) {
            throwException("Failed to parse tool arguments: '%s'".formatted(json), e);
            return null; // unreachable
        }
    }

    private String getRequiredArgument(String argumentName, Map<String, Object> arguments) {
        if (isNullOrEmpty(arguments) || !arguments.containsKey(argumentName)) {
            throwException("Missing required tool argument '%s'".formatted(argumentName));
        }
        return arguments.get(argumentName).toString();
    }

    private void throwException(String message) {
        throwException(message, null);
    }

    private void throwException(String message, Exception e) {
        if (config.throwToolArgumentsExceptions) {
            throw e == null
                    ? new ToolArgumentsException(message)
                    : new ToolArgumentsException(message, e);
        } else {
            throw e == null
                    ? new ToolExecutionException(message)
                    : new ToolExecutionException(message, e);
        }
    }

    Integer resolveTimeout(Map<String, Object> arguments) {
        Object timeoutSeconds = arguments.get(config.timeoutSecondsParameterName);
        if (timeoutSeconds == null) {
            return null;
        }
        if (timeoutSeconds instanceof Integer i) {
            return i;
        }
        return Integer.valueOf(timeoutSeconds.toString());
    }

    private String formatStdOut(String stdOut) {
        return truncate(stdOut, config.maxStdOutChars);
    }

    private String formatStdErr(String stdErr) {
        return truncate(stdErr, config.maxStdErrChars);
    }

    private static String truncate(String text, int maxChars) {
        if (text.length() <= maxChars) return text;
        return "[truncated: showing last " + maxChars + " of " + text.length() + " chars]\n"
                + text.substring(text.length() - maxChars);
    }

    @Override
    public String execute(ToolExecutionRequest request, Object memoryId) {
        throw new IllegalStateException("executeWithContext must be called instead");
    }
}
