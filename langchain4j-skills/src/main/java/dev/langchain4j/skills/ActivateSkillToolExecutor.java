package dev.langchain4j.skills;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.service.tool.ToolExecutionResult;

import java.util.Map;

import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

class ActivateSkillToolExecutor extends AbstractSkillToolExecutor {

    private final ActivateSkillToolConfig config;
    private final Map<String, Skill> skillsByName;

    ActivateSkillToolExecutor(ActivateSkillToolConfig config,
                              Map<String, Skill> skillsByName,
                              boolean throwToolArgumentsExceptions) {
        super(throwToolArgumentsExceptions);
        this.config = ensureNotNull(config, "config");
        this.skillsByName = copy(skillsByName);
    }

    @Override
    public ToolExecutionResult executeWithContext(ToolExecutionRequest request, InvocationContext context) {

        Map<String, Object> arguments = parseArguments(request.arguments());
        String skillName = getRequiredArgument(config.parameterName, arguments);

        Skill skill = skillsByName.get(skillName);
        if (skill == null) {
            throwException("There is no skill with name '%s'".formatted(skillName));
        }

        return ToolExecutionResult.builder()
                .resultText(skill.content())
                .build();
    }
}
