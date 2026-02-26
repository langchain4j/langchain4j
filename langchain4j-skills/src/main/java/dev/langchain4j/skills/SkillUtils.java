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

import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.internal.Utils.toBase64;
import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;

class SkillUtils {

    private static final String DEFAULT_ACTIVATE_SKILL_TOOL_NAME = "activate_skill";
    private static final String DEFAULT_ACTIVATE_SKILL_TOOL_DESCRIPTION = "Activates a skill by name";
    private static final String DEFAULT_ACTIVATE_SKILL_TOOL_ARGUMENT_NAME = "skill_name";
    private static final String DEFAULT_ACTIVATE_SKILL_TOOL_ARGUMENT_DESCRIPTION = "The name of the skill to activate";

    static ToolProvider createToolProvider(Collection<? extends Skill> skills,
                                           boolean allowRunningShellCommands) {
        ensureNotEmpty(skills, "skills");

        Map<String, Skill> skillsByName = new LinkedHashMap<>();
        skills.forEach(skill -> skillsByName.put(skill.name(), skill));

        ToolSpecification activateSkillTool = ToolSpecification.builder()
                .name(DEFAULT_ACTIVATE_SKILL_TOOL_NAME) // TODO make configurable
                .description(DEFAULT_ACTIVATE_SKILL_TOOL_DESCRIPTION) // TODO make configurable
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty(DEFAULT_ACTIVATE_SKILL_TOOL_ARGUMENT_NAME, DEFAULT_ACTIVATE_SKILL_TOOL_ARGUMENT_DESCRIPTION) // TODO make configurable
                        .required(DEFAULT_ACTIVATE_SKILL_TOOL_ARGUMENT_NAME) // TODO make configurable
                        .build())
                .build();

        ToolExecutor activateSkillExecutor = new ToolExecutor() {

            @Override
            public ToolExecutionResult executeWithContext(ToolExecutionRequest request, InvocationContext context) {

                Map<String, Object> arguments = parseArguments(request.arguments());
                String skillName = getArgument(DEFAULT_ACTIVATE_SKILL_TOOL_ARGUMENT_NAME, arguments); // TODO make configurable

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

        if (allowRunningShellCommands) {
            ToolSpecification runShellCommandTool = createRunShellCommandTool(skills);
            ToolExecutor runShellCommandToolExecutor = new RunShellCommandToolExecutor(skillsByName);
            tools.put(runShellCommandTool, runShellCommandToolExecutor);
        } else {
            boolean hasResources = skills.stream().anyMatch(skill -> !skill.resources().isEmpty());
            if (hasResources) {
                String exampleResourcePath = skills.stream()
                        .flatMap(skill -> skill.resources().stream())
                        .findFirst()
                        .map(SkillResource::relativePath)
                        .orElseThrow();

                ToolSpecification readResourceTool = ToolSpecification.builder()
                        .name("read_skill_resource") // TODO make configurable
                        .description("Reads content of a resource referenced in the skill") // TODO make configurable, fix grammar here and everywhere
                        .parameters(JsonObjectSchema.builder()
                                .addStringProperty("skill_name", "The name of the skill for which to read the resource") // TODO make configurable
                                .addStringProperty("relative_path", "Relative path to the resource. For example: " + exampleResourcePath)
                                .required("skill_name", "relative_path")
                                .build())
                        .build();

                ToolExecutor readResourceExecutor = new ToolExecutor() {

                    @Override
                    public ToolExecutionResult executeWithContext(ToolExecutionRequest request, InvocationContext context) {

                        Map<String, Object> arguments = parseArguments(request.arguments());
                        String skillName = getArgument("skill_name", arguments); // TODO make configurable
                        String relativePath = getArgument("relative_path", arguments); // TODO make configurable

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

    private static ToolSpecification createRunShellCommandTool(Collection<? extends Skill> skills) {
        return ToolSpecification.builder()
                .name("run_shell_command") // TODO make configurable
                .description("Runs a shell command using " + System.getProperty("os.name") + ". When skill_name is provided, the command runs with the skill's root directory as the working directory."
                        // TODO
                        + """
                        Each invocation starts a fresh shell — shell variables and working directory changes do not persist between calls, but filesystem writes do.
                        When installing npm packages, always use local installation (npm install <pkg>, never npm install -g <pkg>). \
                        Global packages are not guaranteed to be found by require() in subsequent calls. \
                        Local packages are installed into node_modules/ inside the working directory and are always found automatically.
                        When generating Node.js code to execute via node -e, always output the entire script as a single line with statements separated by semicolons.
                        Never use multi-line strings, as they break across different shells on Windows.""")
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty("command",
                                "The shell command to execute. For example: 'python scripts/process.py --input data.csv'")
                        .addStringProperty("skill_name",
                                "Optional. Name of the skill whose root directory to use as the working directory")
                        .addStringProperty("timeout_seconds",
                                "Optional. Timeout for the command in seconds. Default: 30 seconds") // TODO
                        .required("command")
                        .build())
                .build();
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
