package dev.langchain4j.skills;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.service.tool.ToolExecutionResult;
import dev.langchain4j.skills.ShellCommandRunner.Result;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

class RunShellCommandToolExecutor extends AbstractSkillToolExecutor {

    private final RunShellCommandToolConfig config;
    private final Map<String, Skill> skillsByName;
    private final ExecutorService executorService;

    public RunShellCommandToolExecutor(RunShellCommandToolConfig config,
                                       Map<String, Skill> skillsByName,
                                       ExecutorService executorService,
                                       boolean throwToolArgumentsExceptions) {
        super(throwToolArgumentsExceptions);
        this.config = ensureNotNull(config, "config");
        this.skillsByName = copy(skillsByName);
        this.executorService = ensureNotNull(executorService, "executorService");
    }

    @Override
    public ToolExecutionResult executeWithContext(ToolExecutionRequest request, InvocationContext context) {

        Map<String, Object> arguments = parseArguments(request.arguments());
        String command = getRequiredArgument(config.commandParameterName, arguments);
        String skillName = arguments.containsKey(config.skillNameParameterName) ? arguments.get(config.skillNameParameterName).toString() : null;
        Integer timeoutSeconds = getTimeoutSeconds(arguments);

        Path workingDir = null;
        if (skillName != null && !skillName.isBlank()) {
            Skill skill = skillsByName.get(skillName);
            if (skill == null) {
                throwException("There is no skill with name '%s'".formatted(skillName));
            }
            if (skill instanceof FileSystemSkill fileSystemSkill) {
                workingDir = fileSystemSkill.basePath();
            }
        }

        // The LLM needs to know the absolute path to construct reliable paths
        // across commands, since each invocation starts a fresh shell process.
        Path resolvedWorkingDir = workingDir != null ? workingDir.toAbsolutePath() : Path.of(System.getProperty("user.dir"));

        try {
            Result result = ShellCommandRunner.run(command, workingDir, timeoutSeconds, executorService);

            String stdOut = formatStdOut(result.stdOut());
            if (result.isSuccess()) {
                String resultText = """
                        <working_dir>%s</working_dir>
                        <stdout>%s</stdout>
                        """.formatted(resolvedWorkingDir, stdOut);
                return ToolExecutionResult.builder()
                        .resultText(resultText)
                        .build();
            } else {
                String stdErr = formatStdErr(result.stdErr());
                String resultText = """
                        <working_dir>%s</working_dir>
                        <exit_code>%s</exit_code>
                        <stdout>%s</stdout>
                        <stderr>%s</stderr>
                        """.formatted(resolvedWorkingDir, result.exitCode(), stdOut, stdErr);
                return ToolExecutionResult.builder()
                        .isError(true)
                        .resultText(resultText)
                        .build();
            }
        } catch (ShellCommandRunner.TimeoutException e) {
            String resultText = """
                    <working_dir>%s</working_dir>
                    <std_out>%s</std_out>
                    <std_err>%s</std_err>
                    """.formatted(resolvedWorkingDir, formatStdOut(e.partialStdOut()), formatStdErr(e.partialStdErr()));
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
        return stdOut.isEmpty() ? "(empty)" : truncate(stdOut, config.maxStdOutChars);
    }

    private String formatStdErr(String stdErr) {
        return stdErr.isEmpty() ? "(empty)" : truncate(stdErr, config.maxStdErrChars);
    }

    private static String truncate(String text, int maxChars) {
        if (text.length() <= maxChars) return text;
        return "[truncated: showing last " + maxChars + " of " + text.length() + " chars]\n"
                + text.substring(text.length() - maxChars);
    }

    Integer getTimeoutSeconds(Map<String, Object> arguments) {
        Object timeoutSeconds = arguments.get(config.timeoutSecondsParameterName);
        if (timeoutSeconds == null) {
            return null;
        }
        if (timeoutSeconds instanceof Integer i) {
            return i;
        }
        return Integer.valueOf(timeoutSeconds.toString());
    }
}
