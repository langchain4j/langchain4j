package dev.langchain4j.skills;

import dev.langchain4j.Experimental;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.service.tool.ToolExecutor;
import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.service.tool.ToolProviderResult;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.service.tool.ToolProviderRequest;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static dev.langchain4j.agent.tool.SearchBehavior.ALWAYS_VISIBLE;
import static dev.langchain4j.agent.tool.ToolSpecification.METADATA_SEARCH_BEHAVIOR;
import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;
import static dev.langchain4j.skills.ActivateSkillToolExecutor.ACTIVATED_SKILL_ATTRIBUTE;
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
 *         .toolProvider(skills.toolProvider())
 *         // or, if you already have an MCP tool provider configured:
 *         .toolProviders(mcpToolProvider, skills.toolProvider())
 *
 *         .build();
 * }</pre>
 */
@Experimental
public class Skills {

    private final List<Skill> skills;
    private final ToolProvider toolProvider;
    private final String formattedAvailableSkills;

    public Skills(Builder builder) {
        this.skills = copy(ensureNotEmpty(builder.skills, "skills"));
        this.toolProvider = createToolProvider(builder);
        this.formattedAvailableSkills = formatAvailableSkills(builder.skills);
    }

    /**
     * Returns a single {@linkplain ToolProvider#isDynamic() dynamic} {@link ToolProvider}
     * that exposes skill-related tools to the LLM.
     * <p>
     * The provider always returns {@code activate_skill} (and optionally {@code read_skill_resource}).
     * Skill-scoped tools are included only after the LLM calls {@code activate_skill} for that skill.
     * <p>
     * Pass it to {@code AiServices.builder(...).toolProviders(skills.toolProvider())}.
     */
    public ToolProvider toolProvider() {
        return toolProvider;
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

    private ToolProvider createToolProvider(Builder builder) {
        Map<String, Skill> skillsByName = new LinkedHashMap<>();
        skills.forEach(skill -> skillsByName.put(skill.name(), skill));

        Map<ToolSpecification, ToolExecutor> skillManagementTools = new HashMap<>();

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

        skillManagementTools.put(activateSkillTool, new ActivateSkillToolExecutor(asc, skillsByName));

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

            skillManagementTools.put(readResourceTool, new ReadResourceToolExecutor(rrc, skillsByName));
        }

        ToolProviderResult skillManagementResult = ToolProviderResult.builder().addAll(skillManagementTools).build();

        Map<String, List<ToolProvider>> skillScopedProviders = new LinkedHashMap<>();
        for (Map.Entry<String, Skill> entry : skillsByName.entrySet()) {
            Skill skill = entry.getValue();
            List<ToolProvider> delegates = skill.toolProviders();
            if (delegates != null && !delegates.isEmpty()) {
                skillScopedProviders.put(entry.getKey(), delegates);
            }
        }

        return new ToolProvider() {

            @Override
            public ToolProviderResult provideTools(ToolProviderRequest request) {
                if (skillScopedProviders.isEmpty()) {
                    return skillManagementResult;
                }

                Set<String> activatedSkillNames = getActivatedSkillNames(request.messages());
                if (activatedSkillNames.isEmpty()) {
                    return skillManagementResult;
                }

                Map<ToolSpecification, ToolExecutor> allTools = new HashMap<>(skillManagementResult.tools());
                Set<String> immediateReturnToolNames = new HashSet<>(skillManagementResult.immediateReturnToolNames());

                for (String skillName : activatedSkillNames) {
                    List<ToolProvider> delegates = skillScopedProviders.get(skillName);
                    if (delegates != null) {
                        for (ToolProvider delegate : delegates) {
                            ToolProviderResult delegateResult = delegate.provideTools(request);
                            if (delegateResult != null) {
                                allTools.putAll(delegateResult.tools());
                                immediateReturnToolNames.addAll(delegateResult.immediateReturnToolNames());
                            }
                        }
                    }
                }

                return ToolProviderResult.builder()
                        .addAll(allTools)
                        .immediateReturnToolNames(immediateReturnToolNames)
                        .build();
            }

            @Override
            public boolean isDynamic() {
                return !skillScopedProviders.isEmpty();
            }
        };
    }

    private static Set<String> getActivatedSkillNames(List<ChatMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return Set.of();
        }
        Set<String> activated = new HashSet<>();
        for (ChatMessage message : messages) {
            if (message instanceof ToolExecutionResultMessage toolResult) {
                Object skillName = toolResult.attributes().get(ACTIVATED_SKILL_ATTRIBUTE);
                if (skillName instanceof String name) {
                    activated.add(name);
                }
            }
        }
        return activated;
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
