package dev.langchain4j.skills;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.service.tool.ToolExecutionResult;
import dev.langchain4j.service.tool.ToolExecutor;

import java.util.Map;

import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static dev.langchain4j.skills.Skills.getArgument;
import static dev.langchain4j.skills.Skills.parseArguments;
import static dev.langchain4j.skills.Skills.throwException;

class ActivateSkillToolExecutor implements ToolExecutor {

    private final ActivateSkillToolConfig config;
    private final Map<String, Skill> skillsByName;
    private final boolean throwToolArgumentsExceptions;

    public ActivateSkillToolExecutor(ActivateSkillToolConfig config,
                                     Map<String, Skill> skillsByName,
                                     boolean throwToolArgumentsExceptions) {
        this.config = ensureNotNull(config, "config");
        this.skillsByName = copy(skillsByName);
        this.throwToolArgumentsExceptions = throwToolArgumentsExceptions;
    }

    @Override
    public ToolExecutionResult executeWithContext(ToolExecutionRequest request, InvocationContext context) {

        // TODO move parseArguments and to getArgument abstract tool exec
        Map<String, Object> arguments = parseArguments(request.arguments(), throwToolArgumentsExceptions);
        String skillName = getArgument(config.parameterName, arguments, throwToolArgumentsExceptions);

        Skill skill = skillsByName.get(skillName);
        if (skill == null) {
            throwException("There is no skill with name '%s'".formatted(skillName), throwToolArgumentsExceptions);
        }

        return ToolExecutionResult.builder()
                .resultText(skill.content())
                .build();
    }

    @Override
    public String execute(ToolExecutionRequest request, Object memoryId) {
        throw new IllegalStateException("executeWithContext must be called instead");
    }
}