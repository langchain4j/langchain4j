package dev.langchain4j.skills;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.service.tool.ToolExecutionResult;
import dev.langchain4j.service.tool.ToolExecutor;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

import static dev.langchain4j.skills.SkillUtils.getArgument;
import static dev.langchain4j.skills.SkillUtils.parseArguments;
import static dev.langchain4j.skills.SkillUtils.throwException;

class RunShellCommandToolExecutor implements ToolExecutor {

    private final Map<String, Skill> skillsByName;
    private final String commandParameterName;
    private final String skillNameParameterName;
    private final String timeoutSecondsParameterName;

    public RunShellCommandToolExecutor(Map<String, Skill> skillsByName,
                                       String commandParameterName,
                                       String skillNameParameterName,
                                       String timeoutSecondsParameterName) {
        this.skillsByName = skillsByName;
        this.commandParameterName = commandParameterName;
        this.skillNameParameterName = skillNameParameterName;
        this.timeoutSecondsParameterName = timeoutSecondsParameterName;
    }

    @Override
    public ToolExecutionResult executeWithContext(ToolExecutionRequest request, InvocationContext context) {

        Map<String, Object> arguments = parseArguments(request.arguments());
        String command = getArgument(commandParameterName, arguments);
        String skillName = arguments.containsKey(skillNameParameterName)
                ? arguments.get(skillNameParameterName).toString() : null;
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

        // Resolve the effective CWD so it can be included in every result.
        // The LLM needs to know the absolute path to construct reliable paths
        // across commands, since each invocation starts a fresh shell process.
        Path resolvedCwd = workingDir != null
                ? workingDir.toAbsolutePath()
                : Path.of(System.getProperty("user.dir"));
        String cwdHeader = "Working directory: " + resolvedCwd + "\n"; // TODO opt-in

        try {
            ShellCommandRunner.Result runResult = ShellCommandRunner.run(command, workingDir, timeoutSeconds);
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
            return i; // TODO test
        }
        return Integer.valueOf(timeoutSeconds.toString());
    }

    @Override
    public String execute(ToolExecutionRequest request, Object memoryId) {
        throw new IllegalStateException("executeWithContext must be called instead");
    }
}
