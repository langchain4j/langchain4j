package dev.langchain4j.skills;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.exception.ToolArgumentsException;
import dev.langchain4j.internal.Json;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.service.tool.ToolExecutionResult;
import dev.langchain4j.service.tool.ToolExecutor;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;
import static dev.langchain4j.service.output.ParsingUtils.toBase64;
import static java.util.Arrays.asList;

public class Skills {

    public static Map<ToolSpecification, ToolExecutor> createTools(Skill... skills) {
        return createTools(asList(skills), null);
    }

    public static Map<ToolSpecification, ToolExecutor> createTools(List<Skill> skills) {
        return createTools(skills, null);
    }

    public static Map<ToolSpecification, ToolExecutor> createTools(SkillsConfig config, Skill... skills) {
        return createTools(asList(skills), config);
    }

    public static Map<ToolSpecification, ToolExecutor> createTools(List<Skill> skills, SkillsConfig config) {
        ensureNotEmpty(skills, "skills");

        Map<String, Skill> skillsByName = new LinkedHashMap<>();
        skills.forEach(skill -> skillsByName.put(skill.name(), skill));

        ToolSpecification activateSkillTool = ToolSpecification.builder()
                .name("activate_skill") // TODO make configurable
                .description("Activates a skill") // TODO make configurable
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty("name", "The name of the skill to activate. For example: " + skills.get(0).name()) // TODO make configurable
                        .required("name") // TODO make configurable
                        .build())
                .build();

        ToolExecutor activateSkillExecutor = new ToolExecutor() {

            @Override
            public ToolExecutionResult executeWithContext(ToolExecutionRequest request, InvocationContext context) {

                Map<String, Object> arguments = parseMap(request.arguments());
                String skillName = extractArgument("name", arguments); // TODO customizable

                Skill skill = skillsByName.get(skillName);
                if (skill == null) {
                    throwException("There is no skill with name '%s'".formatted(skillName));
                }

                return ToolExecutionResult.builder()
                        .resultText(skill.body()) // TODO customizable?
                        .build();
            }

            @Override
            public String execute(ToolExecutionRequest request, Object memoryId) {
                throw new IllegalStateException("executeWithContext must be called instead");
            }
        };

        Map<ToolSpecification, ToolExecutor> result = new HashMap<>();
        result.put(activateSkillTool, activateSkillExecutor);

        boolean hasFiles = skills.stream().anyMatch(skill ->
                skill.files().stream().anyMatch(f -> !f.path().startsWith("scripts/")));
        if (hasFiles) {
            String exampleFilePath = skills.stream()
                    .flatMap(skill -> skill.files().stream())
                    .filter(f -> !f.path().startsWith("scripts/"))
                    .findFirst()
                    .map(SkillFile::path)
                    .orElseThrow();

            ToolSpecification readFileTool = ToolSpecification.builder()
                    .name("read_file") // TODO make configurable, make default less generic, to avoid clashes
                    .description("Reads content of a file") // TODO make configurable
                    .parameters(JsonObjectSchema.builder()
                            .addStringProperty("skill_name", "The name of the skill for which to read the file. For example: " + skills.get(0).name()) // TODO make configurable
                            .addStringProperty("file_path", "Relative path to the file. For example: " + exampleFilePath)
                            .required("skill_name", "file_path")
                            .build())
                    .build();

            ToolExecutor readFileExecutor = new ToolExecutor() {

                @Override
                public ToolExecutionResult executeWithContext(ToolExecutionRequest request, InvocationContext context) {

                    Map<String, Object> arguments = parseMap(request.arguments());
                    String skillName = extractArgument("skill_name", arguments); // TODO customizable
                    String filePath = extractArgument("file_path", arguments); // TODO customizable

                    Skill skill = skillsByName.get(skillName);
                    if (skill == null) {
                        throwException("There is no skill with name '%s'".formatted(skillName));
                    }

                    List<SkillFile> files = skill.files().stream()
                            .filter(file -> file.path().equals(filePath)) // TODO customizable
                            .toList();
                    if (files.isEmpty()) {
                        throwException("There is no file with path '%s'".formatted(filePath));
                        // TODO add all available files for this skill
                    }

                    // TODO if matched not exactly, validate that there is no more than 1 file

                    return ToolExecutionResult.builder()
                            .resultText(files.get(0).body()) // TODO customizable?
                            .build();
                }

                @Override
                public String execute(ToolExecutionRequest request, Object memoryId) {
                    throw new IllegalStateException("executeWithContext must be called instead");
                }
            };

            result.put(readFileTool, readFileExecutor);
        }

        if (config != null && config.allowRun()) {
            ToolSpecification runTool = ToolSpecification.builder()
                    .name("run") // TODO make everything customizable
                    .description("Runs a shell command. When skill_name is provided, the command runs with the skill's root directory as the working directory.")
                    .parameters(JsonObjectSchema.builder()
                            .addStringProperty("command",
                                    "The shell command to execute. For example: 'python scripts/process.py --input data.csv'")
                            .addStringProperty("skill_name",
                                    "Optional. Name of the skill whose root directory to use as the working directory. For example: " + skills.get(0).name())
                            .required("command")
                            .build())
                    .build();

            ToolExecutor runExecutor = new ToolExecutor() {

                @Override
                public ToolExecutionResult executeWithContext(ToolExecutionRequest request, InvocationContext context) {

                    Map<String, Object> arguments = parseMap(request.arguments());
                    String command = extractArgument("command", arguments);
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

                    try {
                        ProcessRunner.Result runResult = ProcessRunner.run(
                                command, workingDir, ProcessRunner.DEFAULT_TIMEOUT_SECONDS);
                        if (runResult.isSuccess()) {
                            return ToolExecutionResult.builder()
                                    .resultText(runResult.stdOut()) // TODO truncate, configurable
                                    .build();
                        } else {
                            String errorText = "Exit code: " + runResult.exitCode() + "\n"
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
            };

            result.put(runTool, runExecutor);
        }

        return result;
    }

    private static String extractArgument(String argumentName, Map<String, Object> arguments) {
        if (isNullOrEmpty(arguments) || !arguments.containsKey(argumentName)) {
            throwException("Missing required tool argument '%s'".formatted(argumentName));
        }

        return arguments.get(argumentName).toString();
    }

    private static Map<String, Object> parseMap(String json) {
        try {
            return Json.fromJson(json, Map.class);
        } catch (Exception e) {
            String message = "Failed to parse tool search arguments: '%s' (base64: '%s')".formatted(json, toBase64(json));
            throwException(message, e);
            return null; // unreachable
        }
    }

    private static void throwException(String message) {
        throwException(message, null);
    }

    private static void throwException(String message, Exception e) {
        throw new ToolArgumentsException(message); // TODO
//        if (throwToolArgumentsExceptions) {
//            throw e == null
//                    ? new ToolArgumentsException(message)
//                    : new ToolArgumentsException(message, e);
//        } else {
//            throw e == null
//                    ? new ToolExecutionException(message)
//                    : new ToolExecutionException(message, e);
//        }
    }

    public static String createSystemMessage(Skill... skills) { // TODO name
        return createSystemMessage(asList(skills));
    }

    public static String createSystemMessage(List<Skill> skills) { // TODO better name?
        ensureNotEmpty(skills, "skills");

        StringBuilder sb = new StringBuilder();

        sb.append("You have access to the following skills:\n");
        sb.append("<available_skills>\n");

        for (Skill skill : skills) {
            sb.append("  <skill>\n")
                    .append("    <name>")
                    .append(escapeXml(skill.name()))
                    .append("</name>\n")
                    .append("    <description>")
                    .append(escapeXml(skill.description()))
                    .append("</description>\n")
                    .append("  </skill>\n");
        }

        sb.append("</available_skills>");

        return sb.toString();
    }

    private static String escapeXml(String value) { // TODO bad idea?
        if (value == null) return "";
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
