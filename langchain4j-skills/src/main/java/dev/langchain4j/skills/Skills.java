package dev.langchain4j.skills;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.exception.ToolArgumentsException;
import dev.langchain4j.exception.ToolExecutionException;
import dev.langchain4j.internal.DefaultExecutorProvider;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.service.tool.ToolExecutor;
import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.service.tool.ToolProviderResult;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static dev.langchain4j.agent.tool.SearchBehavior.ALWAYS_VISIBLE;
import static dev.langchain4j.agent.tool.ToolSpecification.METADATA_SEARCH_BEHAVIOR;
import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;
import static java.util.Arrays.asList;

/**
 * Configures and exposes a set of {@link Skill}s to an LLM.
 * <p>
 * Always provides an {@code activate_skill} tool that the LLM uses to load a skill's content.
 * If any of the configured skills have {@link Skill#resources() resources}, a
 * {@code read_skill_resource} tool is also added so the LLM can read them.
 * Alternatively, if {@link Builder#allowRunningShellCommands(Boolean)} is enabled, a
 * {@code run_shell_command} tool is provided instead.
 * <p>
 * Typical usage with an AI Service:
 * <pre>{@code
 * Skills skills = Skills.from(FileSystemSkillLoader.loadSkills(skillsDir));
 *
 * MyAiService service = AiServices.builder(MyAiService.class)
 *         .chatModel(chatModel)
 *         .systemMessage("You have access to the following skills: " + skills.formatNamesAndDescriptions() + "\nWhen the user's request relates to one of these skills, activate it first using the 'activate_skill' tool before proceeding.")
 *         // or, if you already have a system message configured:
 *         .systemMessageTransformer(systemMessage -> systemMessage + "\n\nYou have access to the following skills: " + skills.formatNamesAndDescriptions() + "\nWhen the user's request relates to one of these skills, activate it first using the 'activate_skill' tool before proceeding.")
 *         .toolProvider(skills.toolProvider()) // or .toolProviders(mcpToolProvider, skills.toolProvider())
 *         .build();
 * }</pre>
 */
public class Skills {

    private final List<Skill> skills;
    private final ToolProvider toolProvider;
    private final String namesAndDescriptions;
    private final boolean throwToolArgumentsExceptions;

    public Skills(Builder builder) {
        this.skills = copy(ensureNotEmpty(builder.skills, "skills"));
        this.toolProvider = createToolProvider(builder);
        this.namesAndDescriptions = formatNamesAndDescriptions(builder.skills);
        this.throwToolArgumentsExceptions = getOrDefault(builder.throwToolArgumentsExceptions, false);
    }

    /**
     * Returns the {@link ToolProvider} that exposes the skill tools to the LLM.
     * Pass this to {@code AiServices.builder(...).toolProvider(...)}.
     */
    public ToolProvider toolProvider() {
        return toolProvider;
    }

    /**
     * Returns an XML-formatted string listing all configured skills with their names and descriptions.
     * Intended to be included in the system message to inform the LLM which skills are available.
     */
    public String formatNamesAndDescriptions() {
        return namesAndDescriptions;
    }

    /**
     * Creates a {@code Skills} instance with default configuration from the given collection of skills.
     */
    public static Skills from(Collection<? extends Skill> skills) {
        return builder().skills(skills).build();
    }

    /**
     * Creates a {@code Skills} instance with default configuration from the given skills.
     */
    public static Skills from(Skill... skills) {
        return builder().skills(skills).build();
    }

    public static Builder builder() {
        return new Builder();
    }

    private ToolProvider createToolProvider(Builder builder) {
        Map<String, Skill> skillsByName = new LinkedHashMap<>();
        skills.forEach(skill -> skillsByName.put(skill.name(), skill));

        ActivateSkillToolConfig config = getOrDefault(builder.activateSkillToolConfig, ActivateSkillToolConfig.builder().build());

        ToolSpecification activateSkillTool = ToolSpecification.builder()
                .name(config.name)
                .description(config.description)
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty(config.parameterName, config.parameterDescription)
                        .required(config.parameterName)
                        .build())
                .addMetadata(METADATA_SEARCH_BEHAVIOR, ALWAYS_VISIBLE)
                .build();

        ToolExecutor activateSkillExecutor = new ActivateSkillToolExecutor(config, skillsByName, throwToolArgumentsExceptions);

        Map<ToolSpecification, ToolExecutor> tools = new HashMap<>();
        tools.put(activateSkillTool, activateSkillExecutor);

        if (getOrDefault(builder.allowRunningShellCommands, false)) {
            RunShellCommandToolConfig rsc = getOrDefault(builder.runShellCommandToolConfig, RunShellCommandToolConfig.builder().build());

            ToolSpecification runShellCommandTool = ToolSpecification.builder()
                    .name(rsc.name)
                    .description(rsc.description)
                    .parameters(JsonObjectSchema.builder()
                            .addStringProperty(rsc.commandParameterName, rsc.commandParameterDescription)
                            .addStringProperty(rsc.skillNameParameterName, rsc.skillNameParameterDescription)
                            .addStringProperty(rsc.timeoutSecondsParameterName, rsc.timeoutSecondsParameterDescription)
                            .required(rsc.commandParameterName)
                            .build())
                    .addMetadata(METADATA_SEARCH_BEHAVIOR, ALWAYS_VISIBLE)
                    .build();

            ToolExecutor runShellCommandToolExecutor = new RunShellCommandToolExecutor(
                    rsc,
                    skillsByName,
                    getOrDefault(rsc.executorService, DefaultExecutorProvider::getDefaultExecutorService),
                    throwToolArgumentsExceptions
            );
            tools.put(runShellCommandTool, runShellCommandToolExecutor);
        } else {
            boolean hasResources = skills.stream().anyMatch(skill -> !skill.resources().isEmpty());
            if (hasResources) {
                ReadResourceToolConfig rrc = getOrDefault(builder.readResourceToolConfig, ReadResourceToolConfig.builder().build());

                ToolSpecification readResourceTool = ToolSpecification.builder()
                        .name(rrc.name)
                        .description(rrc.description)
                        .parameters(JsonObjectSchema.builder()
                                .addStringProperty(rrc.skillNameParameterName, rrc.skillNameParameterDescription)
                                .addStringProperty(rrc.relativePathParameterName, resolveRelativePathParameterDescription(rrc))
                                .required(rrc.skillNameParameterName, rrc.relativePathParameterName)
                                .build())
                        .addMetadata(METADATA_SEARCH_BEHAVIOR, ALWAYS_VISIBLE)
                        .build();

                ToolExecutor readResourceExecutor = new ReadResourceToolExecutor(rrc, skillsByName, throwToolArgumentsExceptions);

                tools.put(readResourceTool, readResourceExecutor);
            }
        }

        ToolProviderResult toolProviderResult = ToolProviderResult.builder()
                .addAll(tools)
                .build();

        return request -> toolProviderResult;
    }

    private String resolveRelativePathParameterDescription(ReadResourceToolConfig rrc) {
        if (rrc.relativePathParameterDescription != null) {
            return rrc.relativePathParameterDescription;
        }
        return rrc.relativePathParameterDescriptionProvider.apply(skills);
    }

    private static String formatNamesAndDescriptions(Collection<? extends Skill> skills) {
        StringBuilder sb = new StringBuilder();
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

        /**
         * Sets the skills to make available to the LLM.
         */
        public Builder skills(Collection<? extends Skill> skills) {
            this.skills = skills;
            return this;
        }

        /**
         * Sets the skills to make available to the LLM.
         */
        public Builder skills(Skill... skills) {
            return skills(asList(skills));
        }

        /**
         * Customizes the {@code activate_skill} tool.
         */
        public Builder activateSkillToolConfig(ActivateSkillToolConfig activateSkillToolConfig) {
            this.activateSkillToolConfig = activateSkillToolConfig;
            return this;
        }

        /**
         * Customizes the {@code read_skill_resource} tool.
         */
        public Builder readResourceToolConfig(ReadResourceToolConfig readResourceToolConfig) {
            this.readResourceToolConfig = readResourceToolConfig;
            return this;
        }

        /**
         * When set to {@code true}, enables the {@code run_shell_command} tool,
         * which allows the LLM to execute shell commands.
         * When enabled, the {@code read_skill_resource} tool is not added.
         * <p>
         * Default: {@code false}.
         */
        public Builder allowRunningShellCommands(Boolean allowRunningShellCommands) {
            this.allowRunningShellCommands = allowRunningShellCommands;
            return this;
        }

        /**
         * Customizes the {@code run_shell_command} tool.
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
