package dev.langchain4j.skills;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.service.tool.ToolExecutionResult;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static dev.langchain4j.service.tool.search.ToolSearchService.FOUND_TOOLS_ATTRIBUTE;
import static java.util.stream.Collectors.joining;

class ActivateSkillToolExecutor extends AbstractSkillToolExecutor {

    private final ActivateSkillToolConfig config;
    private final Map<String, Skill> skillsByName;
    private final Map<String, List<String>> skillToolNames;

    ActivateSkillToolExecutor(ActivateSkillToolConfig config,
                              Map<String, Skill> skillsByName,
                              Map<String, List<String>> skillToolNames) {
        super(config.throwToolArgumentsExceptions);
        this.config = ensureNotNull(config, "config");
        this.skillsByName = copy(skillsByName);
        this.skillToolNames = copy(skillToolNames);
    }

    @Override
    public ToolExecutionResult executeWithContext(ToolExecutionRequest request, InvocationContext context) {

        Map<String, Object> arguments = parseArguments(request.arguments());
        String skillName = getRequiredArgument(config.parameterName, arguments);

        Skill skill = skillsByName.get(skillName);
        if (skill == null) {
            String availableSkillNames = skillsByName.keySet().stream()
                    .map(name -> "'" + name + "'")
                    .collect(joining(", "));
            throwException("There is no skill with name '%s'. Available skills: [%s]".formatted(skillName, availableSkillNames));
        }

        Map<String, Object> attributes = Map.of();
        List<String> toolNames = skillToolNames.get(skillName);
        if (toolNames != null && !toolNames.isEmpty()) {
            attributes = Map.of(FOUND_TOOLS_ATTRIBUTE, toolNames); // TODO use another attribute?
        }

        return ToolExecutionResult.builder()
                .result(skill)
                .resultText(skill.content())
                .attributes(attributes)
                .build();
    }
}
