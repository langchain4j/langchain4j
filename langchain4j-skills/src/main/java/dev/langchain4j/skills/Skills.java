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
            int maxStdoutChars = getOrDefault(builder.runShellCommandToolMaxStdoutChars, DEFAULT_RUN_SHELL_COMMAND_TOOL_MAX_STDOUT_CHARS);
            int maxStderrChars = getOrDefault(builder.runShellCommandToolMaxStderrChars, DEFAULT_RUN_SHELL_COMMAND_TOOL_MAX_STDERR_CHARS);
            ToolSpecification runShellCommandTool = createRunShellCommandTool(builder, commandParameterName, runShellSkillNameParameterName, timeoutSecondsParameterName);
            ToolExecutor runShellCommandToolExecutor = new RunShellCommandToolExecutor(skillsByName, commandParameterName, runShellSkillNameParameterName, timeoutSecondsParameterName, executorService, throwToolArgumentsExceptions, maxStdoutChars, maxStderrChars);
            tools.put(runShellCommandTool, runShellCommandToolExecutor);
        } else {
            boolean hasResources = skills.stream().anyMatch(skill -> !skill.resources().isEmpty());
            if (hasResources) {
                String readResourceToolSkillNameParameterName = getOrDefault(builder.readResourceToolSkillNameParameterName, DEFAULT_READ_RESOURCE_TOOL_SKILL_NAME_PARAMETER_NAME);
                String readResourceToolRelativePathParameterName = getOrDefault(builder.readResourceToolRelativePathParameterName, DEFAULT_READ_RESOURCE_TOOL_RELATIVE_PATH_PARAMETER_NAME);

                ToolSpecification readResourceTool = ToolSpecification.builder()
                        .name(getOrDefault(builder.readResourceToolName, DEFAULT_READ_RESOURCE_TOOL_NAME))
                        .description(getOrDefault(builder.readResourceToolDescription, DEFAULT_READ_RESOURCE_TOOL_DESCRIPTION))
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

    private static ToolSpecification createRunShellCommandTool(Builder builder,
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

    private static String resolveRelativePathParameterDescription(Builder builder) {
        if (builder.readResourceToolRelativePathParameterDescription != null) {
            return builder.readResourceToolRelativePathParameterDescription;
        }
        return getOrDefault(
                builder.readResourceToolRelativePathParameterDescriptionProvider,
                DEFAULT_READ_RESOURCE_TOOL_RELATIVE_PATH_PARAMETER_DESCRIPTION_PROVIDER)
                .apply(List.copyOf(builder.skills));
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
        Boolean allowRunningShellCommands;
        ExecutorService executorService;
        Boolean throwToolArgumentsExceptions;

        // activate_skill tool
        String activateSkillToolName;
        String activateSkillToolDescription;
        String activateSkillToolParameterName;
        String activateSkillToolParameterDescription;

        // read_skill_resource tool
        String readResourceToolName;
        String readResourceToolDescription;
        String readResourceToolSkillNameParameterName;
        String readResourceToolSkillNameParameterDescription;
        String readResourceToolRelativePathParameterName;
        String readResourceToolRelativePathParameterDescription;
        Function<List<? extends Skill>, String> readResourceToolRelativePathParameterDescriptionProvider;

        // run_shell_command tool
        String runShellCommandToolName;
        String runShellCommandToolDescription;
        String runShellCommandToolCommandParameterName;
        String runShellCommandToolCommandParameterDescription;
        String runShellCommandToolSkillNameParameterName;
        String runShellCommandToolSkillNameParameterDescription;
        String runShellCommandToolTimeoutSecondsParameterName;
        String runShellCommandToolTimeoutSecondsParameterDescription;
        Integer runShellCommandToolMaxStdoutChars;
        Integer runShellCommandToolMaxStderrChars;

        public Builder skills(Collection<? extends Skill> skills) {
            this.skills = skills;
            return this;
        }

        public Builder skills(Skill... skills) {
            return skills(asList(skills));
        }

        public Builder allowRunningShellCommands(Boolean allowRunningShellCommands) {
            this.allowRunningShellCommands = allowRunningShellCommands;
            return this;
        }

        /**
         * Sets the {@link ExecutorService} used to read the stdout and stderr streams
         * of shell commands submitted via the {@code run_shell_command} tool.
         * <p>
         * By default, {@link dev.langchain4j.internal.DefaultExecutorProvider#getDefaultExecutorService()} is used.
         */
        public Builder executorService(ExecutorService executorService) {
            this.executorService = executorService;
            return this;
        }

        /**
         * Controls which exception type is thrown when tool arguments are missing,
         * invalid, or cannot be parsed.
         * <p>
         * When set to {@code true}, {@link dev.langchain4j.exception.ToolArgumentsException} is thrown.
         * When set to {@code false} (default), {@link dev.langchain4j.exception.ToolExecutionException} is thrown,
         * which allows the error message to be returned to the LLM rather than failing fast.
         * <p>
         * Default value: {@code false}.
         */
        public Builder throwToolArgumentsExceptions(Boolean throwToolArgumentsExceptions) {
            this.throwToolArgumentsExceptions = throwToolArgumentsExceptions;
            return this;
        }

        /**
         * Sets the name of the {@code activate_skill} tool.
         * <p>
         * Default value is {@value Skills#DEFAULT_ACTIVATE_SKILL_TOOL_NAME}.
         */
        public Builder activateSkillToolName(String activateSkillToolName) {
            this.activateSkillToolName = activateSkillToolName;
            return this;
        }

        /**
         * Sets the description of the {@code activate_skill} tool.
         * <p>
         * Default value is {@value Skills#DEFAULT_ACTIVATE_SKILL_TOOL_DESCRIPTION}.
         */
        public Builder activateSkillToolDescription(String activateSkillToolDescription) {
            this.activateSkillToolDescription = activateSkillToolDescription;
            return this;
        }

        /**
         * Sets the name of the parameter that specifies which skill to activate.
         * <p>
         * Default value is {@value Skills#DEFAULT_ACTIVATE_SKILL_TOOL_PARAMETER_NAME}.
         */
        public Builder activateSkillToolParameterName(String activateSkillToolParameterName) {
            this.activateSkillToolParameterName = activateSkillToolParameterName;
            return this;
        }

        /**
         * Sets the description of the parameter that specifies which skill to activate.
         * <p>
         * Default value is {@value Skills#DEFAULT_ACTIVATE_SKILL_TOOL_PARAMETER_DESCRIPTION}.
         */
        public Builder activateSkillToolParameterDescription(String activateSkillToolParameterDescription) {
            this.activateSkillToolParameterDescription = activateSkillToolParameterDescription;
            return this;
        }

        /**
         * Sets the name of the {@code read_skill_resource} tool.
         * <p>
         * Default value is {@value Skills#DEFAULT_READ_RESOURCE_TOOL_NAME}.
         */
        public Builder readResourceToolName(String readResourceToolName) {
            this.readResourceToolName = readResourceToolName;
            return this;
        }

        /**
         * Sets the description of the {@code read_skill_resource} tool.
         * <p>
         * Default value is {@value Skills#DEFAULT_READ_RESOURCE_TOOL_DESCRIPTION}.
         */
        public Builder readResourceToolDescription(String readResourceToolDescription) {
            this.readResourceToolDescription = readResourceToolDescription;
            return this;
        }

        /**
         * Sets the name of the {@code skill_name} parameter of the {@code read_skill_resource} tool.
         * <p>
         * Default value is {@value Skills#DEFAULT_READ_RESOURCE_TOOL_SKILL_NAME_PARAMETER_NAME}.
         */
        public Builder readResourceToolSkillNameParameterName(String readResourceToolSkillNameParameterName) {
            this.readResourceToolSkillNameParameterName = readResourceToolSkillNameParameterName;
            return this;
        }

        /**
         * Sets the description of the {@code skill_name} parameter of the {@code read_skill_resource} tool.
         * <p>
         * Default value is {@value Skills#DEFAULT_READ_RESOURCE_TOOL_SKILL_NAME_PARAMETER_DESCRIPTION}.
         */
        public Builder readResourceToolSkillNameParameterDescription(String readResourceToolSkillNameParameterDescription) {
            this.readResourceToolSkillNameParameterDescription = readResourceToolSkillNameParameterDescription;
            return this;
        }

        /**
         * Sets the name of the {@code relative_path} parameter of the {@code read_skill_resource} tool.
         * <p>
         * Default value is {@value Skills#DEFAULT_READ_RESOURCE_TOOL_RELATIVE_PATH_PARAMETER_NAME}.
         */
        public Builder readResourceToolRelativePathParameterName(String readResourceToolRelativePathParameterName) {
            this.readResourceToolRelativePathParameterName = readResourceToolRelativePathParameterName;
            return this;
        }

        /**
         * Sets the description of the {@code relative_path} parameter of the {@code read_skill_resource} tool.
         * <p>
         * By default, the description is generated dynamically and includes an example path
         * taken from the first available skill resource.
         * <p>
         * Takes precedence over {@link #readResourceToolRelativePathParameterDescriptionProvider(Function)}.
         */
        public Builder readResourceToolRelativePathParameterDescription(String readResourceToolRelativePathParameterDescription) {
            this.readResourceToolRelativePathParameterDescription = readResourceToolRelativePathParameterDescription;
            return this;
        }

        /**
         * Sets a function that produces the description of the {@code relative_path} parameter
         * of the {@code read_skill_resource} tool.
         * <p>
         * The function receives the list of configured skills and returns the full parameter description.
         * This allows customizing the description template while still incorporating dynamic information
         * such as an example path derived from the available skill resources.
         * Ignored if {@link #readResourceToolRelativePathParameterDescription(String)} is set.
         * <p>
         * Default: {@code skills -> "Relative path to the resource. For example: " + <first resource path>}
         */
        public Builder readResourceToolRelativePathParameterDescriptionProvider(Function<List<? extends Skill>, String> readResourceToolRelativePathParameterDescriptionProvider) {
            this.readResourceToolRelativePathParameterDescriptionProvider = readResourceToolRelativePathParameterDescriptionProvider;
            return this;
        }

        /**
         * Sets the name of the {@code run_shell_command} tool.
         * <p>
         * Default value is {@value Skills#DEFAULT_RUN_SHELL_COMMAND_TOOL_NAME}.
         */
        public Builder runShellCommandToolName(String runShellCommandToolName) {
            this.runShellCommandToolName = runShellCommandToolName;
            return this;
        }

        /**
         * Sets the description of the {@code run_shell_command} tool.
         * <p>
         * By default, the description is generated dynamically and includes the current OS name.
         */
        public Builder runShellCommandToolDescription(String runShellCommandToolDescription) {
            this.runShellCommandToolDescription = runShellCommandToolDescription;
            return this;
        }

        /**
         * Sets the name of the {@code command} parameter of the {@code run_shell_command} tool.
         * <p>
         * Default value is {@value Skills#DEFAULT_RUN_SHELL_COMMAND_TOOL_COMMAND_PARAMETER_NAME}.
         */
        public Builder runShellCommandToolCommandParameterName(String runShellCommandToolCommandParameterName) {
            this.runShellCommandToolCommandParameterName = runShellCommandToolCommandParameterName;
            return this;
        }

        /**
         * Sets the description of the {@code command} parameter of the {@code run_shell_command} tool.
         * <p>
         * Default value is {@value Skills#DEFAULT_RUN_SHELL_COMMAND_TOOL_COMMAND_PARAMETER_DESCRIPTION}.
         */
        public Builder runShellCommandToolCommandParameterDescription(String runShellCommandToolCommandParameterDescription) {
            this.runShellCommandToolCommandParameterDescription = runShellCommandToolCommandParameterDescription;
            return this;
        }

        /**
         * Sets the name of the {@code skill_name} parameter of the {@code run_shell_command} tool.
         * <p>
         * Default value is {@value Skills#DEFAULT_RUN_SHELL_COMMAND_TOOL_SKILL_NAME_PARAMETER_NAME}.
         */
        public Builder runShellCommandToolSkillNameParameterName(String runShellCommandToolSkillNameParameterName) {
            this.runShellCommandToolSkillNameParameterName = runShellCommandToolSkillNameParameterName;
            return this;
        }

        /**
         * Sets the description of the {@code skill_name} parameter of the {@code run_shell_command} tool.
         * <p>
         * Default value is {@value Skills#DEFAULT_RUN_SHELL_COMMAND_TOOL_SKILL_NAME_PARAMETER_DESCRIPTION}.
         */
        public Builder runShellCommandToolSkillNameParameterDescription(String runShellCommandToolSkillNameParameterDescription) {
            this.runShellCommandToolSkillNameParameterDescription = runShellCommandToolSkillNameParameterDescription;
            return this;
        }

        /**
         * Sets the name of the {@code timeout_seconds} parameter of the {@code run_shell_command} tool.
         * <p>
         * Default value is {@value Skills#DEFAULT_RUN_SHELL_COMMAND_TOOL_TIMEOUT_SECONDS_PARAMETER_NAME}.
         */
        public Builder runShellCommandToolTimeoutSecondsParameterName(String runShellCommandToolTimeoutSecondsParameterName) {
            this.runShellCommandToolTimeoutSecondsParameterName = runShellCommandToolTimeoutSecondsParameterName;
            return this;
        }

        /**
         * Sets the description of the {@code timeout_seconds} parameter of the {@code run_shell_command} tool.
         * <p>
         * Default value is {@value Skills#DEFAULT_RUN_SHELL_COMMAND_TOOL_TIMEOUT_SECONDS_PARAMETER_DESCRIPTION}.
         */
        public Builder runShellCommandToolTimeoutSecondsParameterDescription(String runShellCommandToolTimeoutSecondsParameterDescription) {
            this.runShellCommandToolTimeoutSecondsParameterDescription = runShellCommandToolTimeoutSecondsParameterDescription;
            return this;
        }

        /**
         * Sets the maximum number of characters of stdout to include in the tool result returned to the LLM.
         * If the output exceeds this limit, the beginning is discarded and a truncation notice is prepended.
         * <p>
         * Default value is {@value Skills#DEFAULT_RUN_SHELL_COMMAND_TOOL_MAX_STDOUT_CHARS}.
         */
        public Builder runShellCommandToolMaxStdoutChars(Integer runShellCommandToolMaxStdoutChars) {
            this.runShellCommandToolMaxStdoutChars = runShellCommandToolMaxStdoutChars;
            return this;
        }

        /**
         * Sets the maximum number of characters of stderr to include in the tool result returned to the LLM.
         * If the output exceeds this limit, the beginning is discarded and a truncation notice is prepended.
         * <p>
         * Default value is {@value Skills#DEFAULT_RUN_SHELL_COMMAND_TOOL_MAX_STDERR_CHARS}.
         */
        public Builder runShellCommandToolMaxStderrChars(Integer runShellCommandToolMaxStderrChars) {
            this.runShellCommandToolMaxStderrChars = runShellCommandToolMaxStderrChars;
            return this;
        }

        public Skills build() {
            return new Skills(this);
        }
    }
}
