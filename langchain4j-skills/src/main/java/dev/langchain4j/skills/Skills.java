package dev.langchain4j.skills;

import dev.langchain4j.Experimental;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.service.tool.ToolExecutor;
import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.service.tool.ToolProviderResult;

import java.util.ArrayList;
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
 * Implements the <strong>Tool-based agents</strong> integration approach from the
 * <a href="https://agentskills.io/integrate-skills">Agent Skills specification</a>:
 * all skill content and resources are loaded into memory at construction time.
 * An {@code activate_skill} tool lets the LLM read a skill's instructions on demand,
 * and an optional {@code read_skill_resource} tool reads preloaded resource content.
 * The LLM has no access to the file system at inference time,
 * only abovementioned tools can be called and there is no risk of arbitrary code execution.
 * <p>
 * Typical usage with an AI Service:
 * <pre>{@code
 * Skills skills = Skills.from(FileSystemSkillLoader.loadSkills(skillsDir));
 *
 * MyAiService service = AiServices.builder(MyAiService.class)
 *         .chatModel(chatModel)
 *
 *         .systemMessage("You have access to the following skills:\n" + skills.formatAvailableSkills() + "\nWhen the user's request relates to one of these skills, activate it first using the `activate_skill` tool before proceeding.")
 *         // or, if you already have a system message configured:
 *         .systemMessageTransformer(systemMessage -> systemMessage + "\n\nYou have access to the following skills:\n" + skills.formatAvailableSkills() + "\nWhen the user's request relates to one of these skills, activate it first using the `activate_skill` tool before proceeding.")
 *
 *         .toolProviders(skills.toolProviders())
 *         // or, if you already have an MCP tool provider configured:
 *         .toolProviders(Stream.concat(Stream.of(mcpToolProvider), skills.toolProviders().stream()).toList())
 *
 *         .build();
 * }</pre>
 */
@Experimental
public class Skills {

    private final List<Skill> skills;
    private final List<ToolProvider> toolProviders;
    private final String formattedAvailableSkills;

    public Skills(Builder builder) {
        this.skills = copy(ensureNotEmpty(builder.skills, "skills"));
        this.toolProviders = createToolProviders(builder);
        this.formattedAvailableSkills = formatAvailableSkills(builder.skills);
    }

    /**
     * Returns the list of {@link ToolProvider}s that expose skill-related tools to the LLM.
     * <p>
     * The list contains:
     * <ul>
     *     <li>A base provider (always active) with {@code activate_skill} and optionally {@code read_skill_resource}</li>
     *     <li>One {@linkplain ToolProvider#isDynamic() dynamic} provider per skill that has tools —
     *         each returns tools only after the LLM calls {@code activate_skill} for that skill</li>
     * </ul>
     * Pass these to {@code AiServices.builder(...).toolProviders(skills.toolProviders())}.
     */
    public List<ToolProvider> toolProviders() {
        return toolProviders;
    }

    /**
     * TODO
     */
    public ToolProvider toolProvider() {
        return null; // TODO
    }

    /**
     * Returns an XML-formatted string listing all configured skills with their names and descriptions.
     * Intended to be included in the system message to inform the LLM which skills are available.
     */
    public String formatAvailableSkills() {
        return formattedAvailableSkills;
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

    private List<ToolProvider> createToolProviders(Builder builder) {
        Map<String, Skill> skillsByName = new LinkedHashMap<>();
        skills.forEach(skill -> skillsByName.put(skill.name(), skill));

        Map<ToolSpecification, ToolExecutor> baseTools = new HashMap<>();

        ActivateSkillToolConfig asc = getOrDefault(builder.activateSkillToolConfig, ActivateSkillToolConfig.builder().build());

        ToolSpecification activateSkillTool = ToolSpecification.builder()
                .name(asc.name)
                .description(asc.description)
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty(asc.parameterName, asc.parameterDescription)
                        .required(asc.parameterName)
                        .build())
                .addMetadata(METADATA_SEARCH_BEHAVIOR, ALWAYS_VISIBLE)
                .build();

        baseTools.put(activateSkillTool, new ActivateSkillToolExecutor(asc, skillsByName));

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

            baseTools.put(readResourceTool, new ReadResourceToolExecutor(rrc, skillsByName));
        }

        List<ToolProvider> providers = new ArrayList<>();

        // Base provider: always active, provides activate_skill and read_skill_resource
        providers.add(request -> ToolProviderResult.builder().addAll(baseTools).build());

        // Per-skill providers: each overrides isActive() to check skill activation
        for (Map.Entry<String, Skill> entry : skillsByName.entrySet()) {
            Skill skill = entry.getValue();
            List<ToolProvider> skillToolProviders = skill.toolProviders();
            if (skillToolProviders != null && !skillToolProviders.isEmpty()) {
                String skillName = entry.getKey();
                providers.add(new SkillToolProvider(skillName, skillToolProviders));
            }
        }

        return List.copyOf(providers);
    }

    private String resolveRelativePathParameterDescription(ReadResourceToolConfig rrc) {
        if (rrc.relativePathParameterDescription != null) {
            return rrc.relativePathParameterDescription;
        }
        return rrc.relativePathParameterDescriptionProvider.apply(skills);
    }

    private static String formatAvailableSkills(Collection<? extends Skill> skills) {
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

        public Skills build() {
            return new Skills(this);
        }
    }
}
