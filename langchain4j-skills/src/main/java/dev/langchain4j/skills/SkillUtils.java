package dev.langchain4j.skills;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.exception.ToolExecutionException;
import dev.langchain4j.internal.Json;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.service.tool.ToolExecutionResult;
import dev.langchain4j.service.tool.ToolExecutor;
import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.service.tool.ToolProviderResult;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;

import static dev.langchain4j.internal.DefaultExecutorProvider.getDefaultExecutorService;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.internal.Utils.toBase64;
import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;

class SkillUtils {

    static final String DEFAULT_ACTIVATE_SKILL_TOOL_NAME = "activate_skill";
    static final String DEFAULT_ACTIVATE_SKILL_TOOL_DESCRIPTION = "Activates a skill by name";
    static final String DEFAULT_ACTIVATE_SKILL_TOOL_PARAMETER_NAME = "skill_name";
    static final String DEFAULT_ACTIVATE_SKILL_TOOL_PARAMETER_DESCRIPTION = "The name of the skill to activate";

    static final String DEFAULT_READ_RESOURCE_TOOL_NAME = "read_skill_resource";
    static final String DEFAULT_READ_RESOURCE_TOOL_DESCRIPTION = "Reads content of a resource referenced in the skill";
    static final String DEFAULT_READ_RESOURCE_TOOL_SKILL_NAME_PARAMETER_NAME = "skill_name";
    static final String DEFAULT_READ_RESOURCE_TOOL_SKILL_NAME_PARAMETER_DESCRIPTION = "The name of the skill for which to read the resource";
    static final String DEFAULT_READ_RESOURCE_TOOL_RELATIVE_PATH_PARAMETER_NAME = "relative_path";
    static final Function<List<? extends Skill>, String> DEFAULT_READ_RESOURCE_TOOL_RELATIVE_PATH_PARAMETER_DESCRIPTION_PROVIDER =
            skills -> "Relative path to the resource. For example: " + skills.stream()
                    .flatMap(skill -> skill.resources().stream())
                    .findFirst()
                    .map(SkillResource::relativePath)
                    .orElseThrow();

    static final String DEFAULT_RUN_SHELL_COMMAND_TOOL_NAME = "run_shell_command";
    static final String DEFAULT_RUN_SHELL_COMMAND_TOOL_DESCRIPTION = "Runs a shell command using " + System.getProperty("os.name") + ". When skill_name is provided, the command runs with the skill's root directory as the working directory.";
    static final String DEFAULT_RUN_SHELL_COMMAND_TOOL_COMMAND_PARAMETER_NAME = "command";
    static final String DEFAULT_RUN_SHELL_COMMAND_TOOL_COMMAND_PARAMETER_DESCRIPTION = "The shell command to execute. For example: 'python scripts/process.py --input data.csv'";
    static final String DEFAULT_RUN_SHELL_COMMAND_TOOL_SKILL_NAME_PARAMETER_NAME = "skill_name";
    static final String DEFAULT_RUN_SHELL_COMMAND_TOOL_SKILL_NAME_PARAMETER_DESCRIPTION = "Name of the skill whose root directory to use as the working directory. This is optional parameter.";
    static final String DEFAULT_RUN_SHELL_COMMAND_TOOL_TIMEOUT_SECONDS_PARAMETER_NAME = "timeout_seconds";
    static final String DEFAULT_RUN_SHELL_COMMAND_TOOL_TIMEOUT_SECONDS_PARAMETER_DESCRIPTION = "Timeout for the command in seconds. This is optional parameter. Default value: 30 seconds"; // TODO

    static ToolProvider createToolProvider(SkillService.Builder builder) {
        Collection<? extends Skill> skills = builder.skills;
        ensureNotEmpty(skills, "skills");

        Map<String, Skill> skillsByName = new LinkedHashMap<>();
        skills.forEach(skill -> skillsByName.put(skill.name(), skill));

        String activateSkillToolParameterName = getOrDefault(builder.activateSkillToolParameterName, DEFAULT_ACTIVATE_SKILL_TOOL_PARAMETER_NAME);

        ToolSpecification activateSkillTool = ToolSpecification.builder()
                .name(getOrDefault(builder.activateSkillToolName, DEFAULT_ACTIVATE_SKILL_TOOL_NAME))
                .description(getOrDefault(builder.activateSkillToolDescription, DEFAULT_ACTIVATE_SKILL_TOOL_DESCRIPTION))
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty(activateSkillToolParameterName, getOrDefault(builder.activateSkillToolParameterDescription, DEFAULT_ACTIVATE_SKILL_TOOL_PARAMETER_DESCRIPTION))
                        .required(activateSkillToolParameterName)
                        .build())
                .build();

        ToolExecutor activateSkillExecutor = new ToolExecutor() {

            @Override
            public ToolExecutionResult executeWithContext(ToolExecutionRequest request, InvocationContext context) {

                Map<String, Object> arguments = parseArguments(request.arguments());
                String skillName = getArgument(activateSkillToolParameterName, arguments);

                Skill skill = skillsByName.get(skillName);
                if (skill == null) {
                    throwException("There is no skill with name '%s'".formatted(skillName));
                }

                return ToolExecutionResult.builder()
                        .resultText(skill.content())
                        .build();
            }

            @Override
            public String execute(ToolExecutionRequest request, Object memoryId) {
                throw new IllegalStateException("executeWithContext must be called instead");
            }
        };

        Map<ToolSpecification, ToolExecutor> tools = new HashMap<>();
        tools.put(activateSkillTool, activateSkillExecutor);

        if (getOrDefault(builder.allowRunningShellCommands, false)) {
            String commandParameterName = getOrDefault(builder.runShellCommandToolCommandParameterName, DEFAULT_RUN_SHELL_COMMAND_TOOL_COMMAND_PARAMETER_NAME);
            String runShellSkillNameParameterName = getOrDefault(builder.runShellCommandToolSkillNameParameterName, DEFAULT_RUN_SHELL_COMMAND_TOOL_SKILL_NAME_PARAMETER_NAME);
            String timeoutSecondsParameterName = getOrDefault(builder.runShellCommandToolTimeoutSecondsParameterName, DEFAULT_RUN_SHELL_COMMAND_TOOL_TIMEOUT_SECONDS_PARAMETER_NAME);
            ExecutorService executorService = getOrDefault(builder.executorService, getDefaultExecutorService());
            ToolSpecification runShellCommandTool = createRunShellCommandTool(builder, commandParameterName, runShellSkillNameParameterName, timeoutSecondsParameterName);
            ToolExecutor runShellCommandToolExecutor = new RunShellCommandToolExecutor(skillsByName, commandParameterName, runShellSkillNameParameterName, timeoutSecondsParameterName, executorService);
            tools.put(runShellCommandTool, runShellCommandToolExecutor);
        } else {
            boolean hasResources = skills.stream().anyMatch(skill -> !skill.resources().isEmpty());
            if (hasResources) {
                String readResourceToolSkillNameParameterName = getOrDefault(builder.readResourceToolSkillNameParameterName, DEFAULT_READ_RESOURCE_TOOL_SKILL_NAME_PARAMETER_NAME);
                String readResourceToolRelativePathParameterName = getOrDefault(builder.readResourceToolRelativePathParameterName, DEFAULT_READ_RESOURCE_TOOL_RELATIVE_PATH_PARAMETER_NAME);

                ToolSpecification readResourceTool = ToolSpecification.builder()
                        .name(getOrDefault(builder.readResourceToolName, DEFAULT_READ_RESOURCE_TOOL_NAME))
                        .description(getOrDefault(builder.readResourceToolDescription, DEFAULT_READ_RESOURCE_TOOL_DESCRIPTION)) // TODO fix grammar here and everywhere
                        .parameters(JsonObjectSchema.builder()
                                .addStringProperty(readResourceToolSkillNameParameterName, getOrDefault(builder.readResourceToolSkillNameParameterDescription, DEFAULT_READ_RESOURCE_TOOL_SKILL_NAME_PARAMETER_DESCRIPTION))
                                .addStringProperty(readResourceToolRelativePathParameterName, resolveRelativePathParameterDescription(builder))
                                .required(readResourceToolSkillNameParameterName, readResourceToolRelativePathParameterName)
                                .build())
                        .build();

                ToolExecutor readResourceExecutor = new ToolExecutor() {

                    @Override
                    public ToolExecutionResult executeWithContext(ToolExecutionRequest request, InvocationContext context) {

                        Map<String, Object> arguments = parseArguments(request.arguments());
                        String skillName = getArgument(readResourceToolSkillNameParameterName, arguments);
                        String relativePath = getArgument(readResourceToolRelativePathParameterName, arguments);

                        Skill skill = skillsByName.get(skillName);
                        if (skill == null) {
                            throwException("There is no skill with name '%s'".formatted(skillName));
                        }

                        List<SkillResource> resources = skill.resources().stream()
                                .filter(resource -> resource.relativePath().equals(relativePath)) // TODO make configurable
                                .toList();
                        if (resources.isEmpty()) {
                            throwException("There is no resource with path '%s'".formatted(relativePath));
                            // TODO add all available resources for this skill?
                        }

                        // TODO if matched not exactly, validate that there is no more than 1 resource

                        return ToolExecutionResult.builder()
                                .resultText(resources.get(0).content())
                                .build();
                    }

                    @Override
                    public String execute(ToolExecutionRequest request, Object memoryId) {
                        throw new IllegalStateException("executeWithContext must be called instead");
                    }
                };

                tools.put(readResourceTool, readResourceExecutor);
            }
        }

        ToolProviderResult toolProviderResult = ToolProviderResult.builder()
                .addAll(tools)
                .build();


        return request -> toolProviderResult;
    }

    private static ToolSpecification createRunShellCommandTool(SkillService.Builder builder,
                                                               String commandParameterName,
                                                               String skillNameParameterName,
                                                               String timeoutSecondsParameterName) {
        return ToolSpecification.builder()
                .name(getOrDefault(builder.runShellCommandToolName, DEFAULT_RUN_SHELL_COMMAND_TOOL_NAME))
                .description(getOrDefault(builder.runShellCommandToolDescription, DEFAULT_RUN_SHELL_COMMAND_TOOL_DESCRIPTION))
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty(
                                commandParameterName,
                                getOrDefault(builder.runShellCommandToolCommandParameterDescription, DEFAULT_RUN_SHELL_COMMAND_TOOL_COMMAND_PARAMETER_DESCRIPTION))
                        .addStringProperty(
                                skillNameParameterName,
                                getOrDefault(builder.runShellCommandToolSkillNameParameterDescription, DEFAULT_RUN_SHELL_COMMAND_TOOL_SKILL_NAME_PARAMETER_DESCRIPTION))
                        .addStringProperty(
                                timeoutSecondsParameterName,
                                getOrDefault(builder.runShellCommandToolTimeoutSecondsParameterDescription, DEFAULT_RUN_SHELL_COMMAND_TOOL_TIMEOUT_SECONDS_PARAMETER_DESCRIPTION))
                        .required(commandParameterName)
                        .build())
                .build();
    }

    private static String resolveRelativePathParameterDescription(SkillService.Builder builder) {
        if (builder.readResourceToolRelativePathParameterDescription != null) {
            return builder.readResourceToolRelativePathParameterDescription;
        }
        return getOrDefault(
                builder.readResourceToolRelativePathParameterDescriptionProvider,
                DEFAULT_READ_RESOURCE_TOOL_RELATIVE_PATH_PARAMETER_DESCRIPTION_PROVIDER)
                .apply(List.copyOf(builder.skills));
    }

    public static String getArgument(String argumentName, Map<String, Object> arguments) {
        if (isNullOrEmpty(arguments) || !arguments.containsKey(argumentName)) {
            throwException("Missing required tool argument '%s'".formatted(argumentName));
        }

        return arguments.get(argumentName).toString();
    }

    public static Map<String, Object> parseArguments(String json) {
        try {
            return Json.fromJson(json, Map.class);
        } catch (Exception e) {
            String message = "Failed to parse tool search arguments: '%s' (base64: '%s')".formatted(json, toBase64(json));
            throwException(message, e);
            return null; // unreachable
        }
    }

    public static void throwException(String message) {
        throwException(message, null);
    }

    private static void throwException(String message, Exception e) {
        throw new ToolExecutionException(message); // TODO
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

    static String createSystemMessage(Collection<? extends Skill> skills) {
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

    static String escapeXml(String value) { // TODO bad idea?
        if (value == null) return "";
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
