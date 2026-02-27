package dev.langchain4j.skills;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.exception.ToolArgumentsException;
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
import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.internal.Utils.toBase64;
import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;
import static dev.langchain4j.skills.ShellCommandRunner.DEFAULT_TIMEOUT_SECONDS;
import static java.util.Arrays.asList;
import static java.util.stream.Collectors.joining;

public class Skills {

    // TODO fix grammar here and everywhere
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
    static final String DEFAULT_RUN_SHELL_COMMAND_TOOL_TIMEOUT_SECONDS_PARAMETER_DESCRIPTION = "Timeout for the command in seconds. This is optional parameter. Default value: %s seconds".formatted(DEFAULT_TIMEOUT_SECONDS);
    static final int DEFAULT_RUN_SHELL_COMMAND_TOOL_MAX_STDOUT_CHARS = 10_000;
    static final int DEFAULT_RUN_SHELL_COMMAND_TOOL_MAX_STDERR_CHARS = 10_000;

    private final List<Skill> skills;
    private final ToolProvider toolProvider;
    private final boolean throwToolArgumentsExceptions;

    public Skills(Builder builder) {
        this.skills = copy(ensureNotEmpty(builder.skills, "skills"));
        this.toolProvider = createToolProvider(builder);
        this.throwToolArgumentsExceptions = getOrDefault(builder.throwToolArgumentsExceptions, false);
    }

    public ToolProvider toolProvider() {
        return toolProvider;
    }

    public String systemMessage() {
        return createSystemMessage(skills);
    }

    public static Skills from(Collection<? extends Skill> skills) {
        return builder().skills(skills).build();
    }

    public static Skills from(Skill... skills) {
        return builder().skills(skills).build();
    }

    public static Builder builder() {
        return new Builder();
    }

    private ToolProvider createToolProvider(Builder builder) {
        Map<String, Skill> skillsByName = new LinkedHashMap<>();
        skills.forEach(skill -> skillsByName.put(skill.name(), skill));

        ActivateSkillToolConfig a = builder.activateSkillToolConfig != null ? builder.activateSkillToolConfig : ActivateSkillToolConfig.builder().build();
        String activateSkillToolParameterName = getOrDefault(a.parameterName, DEFAULT_ACTIVATE_SKILL_TOOL_PARAMETER_NAME);

        ToolSpecification activateSkillTool = ToolSpecification.builder()
                .name(getOrDefault(a.name, DEFAULT_ACTIVATE_SKILL_TOOL_NAME))
                .description(getOrDefault(a.description, DEFAULT_ACTIVATE_SKILL_TOOL_DESCRIPTION))
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty(activateSkillToolParameterName, getOrDefault(a.parameterDescription, DEFAULT_ACTIVATE_SKILL_TOOL_PARAMETER_DESCRIPTION))
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
            RunShellCommandToolConfig rsc = builder.runShellCommandToolConfig != null ? builder.runShellCommandToolConfig : RunShellCommandToolConfig.builder().build();
            String commandParameterName = getOrDefault(rsc.commandParameterName, DEFAULT_RUN_SHELL_COMMAND_TOOL_COMMAND_PARAMETER_NAME);
            String runShellSkillNameParameterName = getOrDefault(rsc.skillNameParameterName, DEFAULT_RUN_SHELL_COMMAND_TOOL_SKILL_NAME_PARAMETER_NAME);
            String timeoutSecondsParameterName = getOrDefault(rsc.timeoutSecondsParameterName, DEFAULT_RUN_SHELL_COMMAND_TOOL_TIMEOUT_SECONDS_PARAMETER_NAME);
            ExecutorService executorService = getOrDefault(rsc.executorService, getDefaultExecutorService());
            int maxStdOutChars = getOrDefault(rsc.maxStdOutChars, DEFAULT_RUN_SHELL_COMMAND_TOOL_MAX_STDOUT_CHARS);
            int maxStdErrChars = getOrDefault(rsc.maxStdErrChars, DEFAULT_RUN_SHELL_COMMAND_TOOL_MAX_STDERR_CHARS);
            ToolSpecification runShellCommandTool = createRunShellCommandTool(rsc, commandParameterName, runShellSkillNameParameterName, timeoutSecondsParameterName);
            ToolExecutor runShellCommandToolExecutor = new RunShellCommandToolExecutor(skillsByName, commandParameterName, runShellSkillNameParameterName, timeoutSecondsParameterName, executorService, throwToolArgumentsExceptions, maxStdOutChars, maxStdErrChars);
            tools.put(runShellCommandTool, runShellCommandToolExecutor);
        } else {
            boolean hasResources = skills.stream().anyMatch(skill -> !skill.resources().isEmpty());
            if (hasResources) {
                ReadResourceToolConfig rrc = builder.readResourceToolConfig != null ? builder.readResourceToolConfig : ReadResourceToolConfig.builder().build();
                String readResourceToolSkillNameParameterName = getOrDefault(rrc.skillNameParameterName, DEFAULT_READ_RESOURCE_TOOL_SKILL_NAME_PARAMETER_NAME);
                String readResourceToolRelativePathParameterName = getOrDefault(rrc.relativePathParameterName, DEFAULT_READ_RESOURCE_TOOL_RELATIVE_PATH_PARAMETER_NAME);

                ToolSpecification readResourceTool = ToolSpecification.builder()
                        .name(getOrDefault(rrc.name, DEFAULT_READ_RESOURCE_TOOL_NAME))
                        .description(getOrDefault(rrc.description, DEFAULT_READ_RESOURCE_TOOL_DESCRIPTION))
                        .parameters(JsonObjectSchema.builder()
                                .addStringProperty(readResourceToolSkillNameParameterName, getOrDefault(rrc.skillNameParameterDescription, DEFAULT_READ_RESOURCE_TOOL_SKILL_NAME_PARAMETER_DESCRIPTION))
                                .addStringProperty(readResourceToolRelativePathParameterName, resolveRelativePathParameterDescription(rrc))
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
                                .filter(resource -> resource.relativePath().equals(relativePath))
                                .toList();
                        if (resources.isEmpty()) {
                            String availableResources = skill.resources().stream()
                                    .map(resource -> "'" + resource.relativePath() + "'")
                                    .collect(joining(", "));
                            throwException("There is no resource for skill '%s' with the path '%s'. Available resources: [%s]".formatted(skillName, relativePath, availableResources));
                        }

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

    private static ToolSpecification createRunShellCommandTool(RunShellCommandToolConfig rsc,
                                                               String commandParameterName,
                                                               String skillNameParameterName,
                                                               String timeoutSecondsParameterName) {
        return ToolSpecification.builder()
                .name(getOrDefault(rsc.name, DEFAULT_RUN_SHELL_COMMAND_TOOL_NAME))
                .description(getOrDefault(rsc.description, DEFAULT_RUN_SHELL_COMMAND_TOOL_DESCRIPTION))
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty(
                                commandParameterName,
                                getOrDefault(rsc.commandParameterDescription, DEFAULT_RUN_SHELL_COMMAND_TOOL_COMMAND_PARAMETER_DESCRIPTION))
                        .addStringProperty(
                                skillNameParameterName,
                                getOrDefault(rsc.skillNameParameterDescription, DEFAULT_RUN_SHELL_COMMAND_TOOL_SKILL_NAME_PARAMETER_DESCRIPTION))
                        .addStringProperty(
                                timeoutSecondsParameterName,
                                getOrDefault(rsc.timeoutSecondsParameterDescription, DEFAULT_RUN_SHELL_COMMAND_TOOL_TIMEOUT_SECONDS_PARAMETER_DESCRIPTION))
                        .required(commandParameterName)
                        .build())
                .build();
    }

    private String resolveRelativePathParameterDescription(ReadResourceToolConfig rrc) {
        if (rrc.relativePathParameterDescription != null) {
            return rrc.relativePathParameterDescription;
        }
        return getOrDefault(
                rrc.relativePathParameterDescriptionProvider,
                DEFAULT_READ_RESOURCE_TOOL_RELATIVE_PATH_PARAMETER_DESCRIPTION_PROVIDER)
                .apply(skills);
    }

    private String getArgument(String argumentName, Map<String, Object> arguments) {
        return getArgument(argumentName, arguments, throwToolArgumentsExceptions);
    }

    static String getArgument(String argumentName, Map<String, Object> arguments, boolean throwToolArgumentsExceptions) {
        if (isNullOrEmpty(arguments) || !arguments.containsKey(argumentName)) {
            throwException("Missing required tool argument '%s'".formatted(argumentName), throwToolArgumentsExceptions);
        }

        return arguments.get(argumentName).toString();
    }

    private Map<String, Object> parseArguments(String json) {
        return parseArguments(json, throwToolArgumentsExceptions);
    }

    static Map<String, Object> parseArguments(String json, boolean throwToolArgumentsExceptions) {
        try {
            return Json.fromJson(json, Map.class);
        } catch (Exception e) {
            String message = "Failed to parse tool search arguments: '%s' (base64: '%s')".formatted(json, toBase64(json));
            throwException(message, e, throwToolArgumentsExceptions);
            return null; // unreachable
        }
    }

    void throwException(String message) {
        throwException(message, throwToolArgumentsExceptions);
    }

    static void throwException(String message, boolean throwToolArgumentsExceptions) {
        throwException(message, null, throwToolArgumentsExceptions);
    }

    private void throwException(String message, Exception e) {
        throwException(message, e, throwToolArgumentsExceptions);
    }

    static void throwException(String message, Exception e, boolean throwToolArgumentsExceptions) {
        if (throwToolArgumentsExceptions) {
            throw e == null
                    ? new ToolArgumentsException(message)
                    : new ToolArgumentsException(message, e);
        } else {
            throw e == null
                    ? new ToolExecutionException(message)
                    : new ToolExecutionException(message, e);
        }
    }

    private static String createSystemMessage(List<Skill> skills) {
        ensureNotEmpty(skills, "skills");

        StringBuilder sb = new StringBuilder();

        sb.append("You have access to the following skills:\n"); // TODO customizable
        sb.append("<available_skills>\n");

        for (Skill skill : skills) {
            sb.append("<skill>\n")
                    .append("<name>")
                    .append(escapeXml(skill.name()))
                    .append("</name>\n")
                    .append("<description>")
                    .append(escapeXml(skill.description()))
                    .append("</description>\n")
                    .append("</skill>\n");
        }

        sb.append("</available_skills>");

        return sb.toString();
    }

    private static String escapeXml(String input) {
        if (input == null) {
            return null;
        }
        return input
                .replace("&", "&amp;") // must be first
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    public static class Builder {

        Collection<? extends Skill> skills;
        ActivateSkillToolConfig activateSkillToolConfig;
        ReadResourceToolConfig readResourceToolConfig;
        Boolean allowRunningShellCommands;
        RunShellCommandToolConfig runShellCommandToolConfig;
        Boolean throwToolArgumentsExceptions;

        public Builder skills(Collection<? extends Skill> skills) {
            this.skills = skills;
            return this;
        }

        public Builder skills(Skill... skills) {
            return skills(asList(skills));
        }

        /**
         * Configures the {@code activate_skill} tool.
         */
        public Builder activateSkillToolConfig(ActivateSkillToolConfig activateSkillToolConfig) {
            this.activateSkillToolConfig = activateSkillToolConfig;
            return this;
        }

        /**
         * Configures the {@code read_skill_resource} tool.
         */
        public Builder readResourceToolConfig(ReadResourceToolConfig readResourceToolConfig) {
            this.readResourceToolConfig = readResourceToolConfig;
            return this;
        }

        /**
         * TODO document
         */
        public Builder allowRunningShellCommands(Boolean allowRunningShellCommands) {
            this.allowRunningShellCommands = allowRunningShellCommands;
            return this;
        }

        /**
         * Configures the {@code run_shell_command} tool.
         */
        public Builder runShellCommandToolConfig(RunShellCommandToolConfig runShellCommandToolConfig) {
            this.runShellCommandToolConfig = runShellCommandToolConfig;
            return this;
        }

        /**
         * Controls which exception type is thrown when tool arguments
         * are missing, invalid, or cannot be parsed.
         * <p>
         * Although all errors produced by this tool are argument-related,
         * this strategy throws {@link ToolExecutionException} by default
         * instead of {@link ToolArgumentsException}.
         * <p>
         * The reason is historical: by default, AI Services fail fast when
         * a {@link ToolArgumentsException} is thrown, whereas
         * {@link ToolExecutionException} allows the error message to be
         * returned to the LLM. For these tools, returning the error message
         * to the LLM is usually the desired behavior.
         * <p>
         * If this flag is set to {@code true}, {@link ToolArgumentsException}
         * will be thrown instead.
         *
         * @param throwToolArgumentsExceptions whether to throw {@link ToolArgumentsException}
         * @return this builder
         */
        public Builder throwToolArgumentsExceptions(Boolean throwToolArgumentsExceptions) {
            this.throwToolArgumentsExceptions = throwToolArgumentsExceptions;
            return this;
        }

        public Skills build() {
            return new Skills(this);
        }
    }
}
