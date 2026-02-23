package dev.langchain4j.skills.scripts;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.service.tool.ToolExecutionResult;
import dev.langchain4j.service.tool.ToolExecutor;
import dev.langchain4j.skills.DefaultSkill;
import dev.langchain4j.skills.Skill;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

import static dev.langchain4j.skills.Skills.getArgument;
import static dev.langchain4j.skills.Skills.parseArguments;
import static dev.langchain4j.skills.Skills.throwException;

public class RunScriptToolExecutor implements ToolExecutor { // TODO name

        private final Map<String, Skill> skillsByName; // TODO needed?

        public RunScriptToolExecutor(Map<String, Skill> skillsByName) {
            this.skillsByName = skillsByName;
        }

        @Override
        public ToolExecutionResult executeWithContext(ToolExecutionRequest request, InvocationContext context) {

            Map<String, Object> arguments = parseArguments(request.arguments());
            String command = getArgument("command", arguments);
            String skillName = arguments.containsKey("skill_name")
                    ? arguments.get("skill_name").toString() : null;

            Path workingDir = null;
            if (skillName != null && !skillName.isBlank()) {
                Skill skill = skillsByName.get(skillName);
                if (skill == null) {
                    throwException("There is no skill with name '%s'".formatted(skillName));
                }
                if (skill instanceof DefaultSkill ds && ds.directory() != null) {
                    workingDir = ds.directory();
                }
                // if directory is null (programmatically built skill), CWD falls back to JVM default
            }

            // Resolve the effective CWD so it can be included in every result.
            // The LLM needs to know the absolute path to construct reliable paths
            // across commands, since each invocation starts a fresh shell process.
            Path resolvedCwd = workingDir != null
                    ? workingDir.toAbsolutePath()
                    : Path.of(System.getProperty("user.dir"));
            String cwdHeader = "Working directory: " + resolvedCwd + "\n";

            try {
                ProcessRunner.Result runResult = ProcessRunner.run(
                        command, workingDir, ProcessRunner.DEFAULT_TIMEOUT_SECONDS);
                if (runResult.isSuccess()) {
                    String output = runResult.stdOut().isBlank() ? "(no output)" : runResult.stdOut();
                    return ToolExecutionResult.builder()
                            .resultText(cwdHeader + "Output: " + output) // TODO truncate, configurable
                            .build();
                } else {
                    String errorText = cwdHeader
                            + "Exit code: " + runResult.exitCode() + "\n"
                            + "Stdout:\n" + (runResult.stdOut().isEmpty() ? "(empty)" : runResult.stdOut()) + "\n"
                            + "Stderr:\n" + (runResult.stdErr().isEmpty() ? "(empty)" : runResult.stdErr());
                    return ToolExecutionResult.builder()
                            .isError(true)
                            .resultText(errorText)
                            .build();
                }
            } catch (ProcessRunner.TimeoutException e) {
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

        @Override
        public String execute(ToolExecutionRequest request, Object memoryId) {
            throw new IllegalStateException("executeWithContext must be called instead");
        }
    }