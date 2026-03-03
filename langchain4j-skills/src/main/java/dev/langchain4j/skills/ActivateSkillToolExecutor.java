package dev.langchain4j.skills;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.service.tool.ToolExecutionResult;

import java.util.Map;

import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static java.util.stream.Collectors.joining;

class ActivateSkillToolExecutor extends AbstractSkillToolExecutor {

    private final ActivateSkillToolConfig config;
    private final Map<String, Skill> skillsByName;

    ActivateSkillToolExecutor(ActivateSkillToolConfig config, Map<String, Skill> skillsByName) {
        super(config.throwToolArgumentsExceptions);
        this.config = ensureNotNull(config, "config");
        this.skillsByName = copy(skillsByName);
    }

    @Override
    public ToolExecutionResult executeWithContext(ToolExecutionRequest request, InvocationContext context) {

        Map<String, Object> arguments = parseArguments(request.arguments());
        String skillName = getRequiredArgument(config.parameterName, arguments);

        Skill skill = skillsByName.get(skillName);
        if (skill == null) {
            String availableNames = skillsByName.keySet().stream()
                    .map(name -> "'" + name + "'")
                    .collect(joining(", "));
            throwException("There is no skill with name '%s'. Available skills: [%s]".formatted(skillName, availableNames));
        }

        String resultText = skill.content();
        if (!skill.resources().isEmpty()) {
            String resourceList = skill.resources().stream()
                    .map(r -> "- " + r.relativePath())
                    .collect(joining("\n"));
            resultText += "\n\nAvailable resources:\n" + resourceList;
        }

        return ToolExecutionResult.builder()
                .result(skill)
                .resultText(resultText)
                .build();
    }
}
