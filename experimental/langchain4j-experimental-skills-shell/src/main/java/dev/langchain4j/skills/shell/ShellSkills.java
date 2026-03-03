package dev.langchain4j.skills.shell;

import dev.langchain4j.Experimental;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.service.tool.ToolExecutor;
import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.service.tool.ToolProviderResult;
import dev.langchain4j.skills.FileSystemSkill;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static dev.langchain4j.agent.tool.SearchBehavior.ALWAYS_VISIBLE;
import static dev.langchain4j.agent.tool.ToolSpecification.METADATA_SEARCH_BEHAVIOR;
import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;

/**
 * Configures and exposes a set of {@link FileSystemSkill}s to an LLM using shell commands.
 * <p>
 * <strong>WARNING:
 * Shell execution is inherently unsafe.
 * Commands run directly in the host process environment without any sandboxing,
 * containerization, or privilege restriction. A misbehaving or prompt-injected LLM can
 * execute arbitrary commands on the machine running your application.
 * Only use this in controlled environments where you fully trust the input.</strong>
 * <p>
 * This corresponds to the <strong>Filesystem-based agents</strong> integration approach from the
 * <a href="https://agentskills.io/integrate-skills">Agent Skills specification</a>.
 * <p>
 * Only a {@code run_shell_command} tool is registered. The LLM reads {@code SKILL.md} files
 * and other resources directly via shell commands using the absolute paths provided in the
 * system message TODO via {@code <location>}.
 * <p>
 * Typical usage with an AI Service:
 * <pre>{@code
 * ShellSkills skills = ShellSkills.from(FileSystemSkillLoader.loadSkills(skillsDir));
 *
 * MyAiService service = AiServices.builder(MyAiService.class)
 *         .chatModel(chatModel)
 *         .systemMessage("You have access to the following skills:\n" + skills.formatNamesAndDescriptions() TODO
 *                 + "\nWhen the user's request relates to one of these skills, read its SKILL.md before proceeding.")
 *         .toolProvider(skills.toolProvider())
 *         .build();
 * }</pre>
 */
@Experimental
public class ShellSkills {

    private final List<FileSystemSkill> skills;
    private final ToolProvider toolProvider;
    private final String namesAndDescriptions;

    public ShellSkills(Builder builder) {
        this.skills = copy(ensureNotEmpty(builder.skills, "skills"));
        this.toolProvider = createToolProvider(builder);
        this.namesAndDescriptions = formatNamesAndDescriptions(this.skills);
    }

    /**
     * Returns the {@link ToolProvider} that exposes the {@code run_shell_command} tool to the LLM.
     * Pass this to {@code AiServices.builder(...).toolProvider(...)}.
     */
    public ToolProvider toolProvider() {
        return toolProvider;
    }

    /**
     * Returns an XML-formatted string listing all configured skills with their names, descriptions,
     * and absolute file-system paths to their {@code SKILL.md} files.
     * <p>
     * Include this in the system message so the LLM knows which skills are available and
     * where to find their instructions via {@code run_shell_command}.
     * <p>
     * Example output:
     * <pre>{@code
     * <available_skills>
     * <skill>
     * <name>docx</name>
     * <description>Edit and review Word documents using tracked changes</description>
     * <location>/path/to/skills/docx/SKILL.md</location>
     * </skill>
     * </available_skills>
     * }</pre>
     */
    public String formatNamesAndDescriptions() {
        return namesAndDescriptions;
    } // TODO name: also locations

    /**
     * Creates a {@code ShellSkills} instance with default configuration from the given collection of skills.
     */
    public static ShellSkills from(Collection<? extends FileSystemSkill> skills) {
        return builder().skills(skills).build();
    }

    /**
     * Creates a {@code ShellSkills} instance with default configuration from the given skills.
     */
    public static ShellSkills from(FileSystemSkill... skills) {
        return builder().skills(skills).build();
    }

    public static Builder builder() {
        return new Builder();
    }

    private ToolProvider createToolProvider(Builder builder) {
        RunShellCommandToolConfig rsc = getOrDefault(builder.runShellCommandToolConfig, RunShellCommandToolConfig.builder().build());

        ToolSpecification runShellCommandTool = ToolSpecification.builder()
                .name(rsc.name)
                .description(rsc.description)
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty(rsc.commandParameterName, rsc.commandParameterDescription)
                        .addStringProperty(rsc.timeoutSecondsParameterName, rsc.timeoutSecondsParameterDescription)
                        .required(rsc.commandParameterName)
                        .build())
                .addMetadata(METADATA_SEARCH_BEHAVIOR, ALWAYS_VISIBLE)
                .build();

        ToolExecutor runShellCommandToolExecutor = new RunShellCommandToolExecutor(rsc);

        Map<ToolSpecification, ToolExecutor> tools = new HashMap<>();
        tools.put(runShellCommandTool, runShellCommandToolExecutor);

        ToolProviderResult toolProviderResult = ToolProviderResult.builder()
                .addAll(tools)
                .build();

        return request -> toolProviderResult;
    }

    private static String formatNamesAndDescriptions(List<FileSystemSkill> skills) {
        StringBuilder sb = new StringBuilder();
        sb.append("<available_skills>\n");
        for (FileSystemSkill skill : skills) {
            sb.append("<skill>\n")
                    .append("<name>")
                    .append(escapeXml(skill.name()))
                    .append("</name>\n")
                    .append("<description>")
                    .append(escapeXml(skill.description()))
                    .append("</description>\n")
                    .append("<location>")
                    .append(skill.basePath().toAbsolutePath().resolve("SKILL.md"))
                    .append("</location>\n")
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

        Collection<? extends FileSystemSkill> skills;
        RunShellCommandToolConfig runShellCommandToolConfig;

        /**
         * Sets the filesystem-based skills to make available to the LLM.
         */
        public Builder skills(Collection<? extends FileSystemSkill> skills) {
            this.skills = skills;
            return this;
        }

        /**
         * Sets the filesystem-based skills to make available to the LLM.
         */
        public Builder skills(FileSystemSkill... skills) {
            return skills(Arrays.asList(skills));
        }

        /**
         * Customizes the {@code run_shell_command} tool.
         */
        public Builder runShellCommandToolConfig(RunShellCommandToolConfig runShellCommandToolConfig) {
            this.runShellCommandToolConfig = runShellCommandToolConfig;
            return this;
        }

        public ShellSkills build() {
            return new ShellSkills(this);
        }
    }
}
