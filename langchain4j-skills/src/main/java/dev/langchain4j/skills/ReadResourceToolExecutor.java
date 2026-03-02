package dev.langchain4j.skills;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.service.tool.ToolExecutionResult;
import dev.langchain4j.service.tool.ToolExecutor;

import java.util.List;
import java.util.Map;

import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static dev.langchain4j.skills.Skills.getArgument;
import static dev.langchain4j.skills.Skills.parseArguments;
import static dev.langchain4j.skills.Skills.throwException;
import static java.util.stream.Collectors.joining;

class ReadResourceToolExecutor implements ToolExecutor {

    private final ReadResourceToolConfig config;
    private final Map<String, Skill> skillsByName;
    private final boolean throwToolArgumentsExceptions;

    public ReadResourceToolExecutor(ReadResourceToolConfig config,
                                    Map<String, Skill> skillsByName,
                                    boolean throwToolArgumentsExceptions) {
        this.config = ensureNotNull(config, "config");
        this.skillsByName = copy(skillsByName);
        this.throwToolArgumentsExceptions = throwToolArgumentsExceptions;
    }

    @Override
    public ToolExecutionResult executeWithContext(ToolExecutionRequest request, InvocationContext context) {

        Map<String, Object> arguments = parseArguments(request.arguments(), throwToolArgumentsExceptions);
        String skillName = getArgument(config.skillNameParameterName, arguments, throwToolArgumentsExceptions);
        String relativePath = getArgument(config.relativePathParameterName, arguments, throwToolArgumentsExceptions);

        Skill skill = skillsByName.get(skillName);
        if (skill == null) {
            throwException("There is no skill with name '%s'".formatted(skillName), throwToolArgumentsExceptions);
        }

        List<SkillResource> resources = skill.resources().stream()
                .filter(resource -> resource.relativePath().equals(relativePath))
                .toList();
        if (resources.isEmpty()) {
            String availableResources = skill.resources().stream()
                    .map(resource -> "'" + resource.relativePath() + "'")
                    .collect(joining(", "));
            throwException("There is no resource for skill '%s' with the path '%s'. Available resources: [%s]".formatted(skillName, relativePath, availableResources), throwToolArgumentsExceptions);
        }

        return ToolExecutionResult.builder()
                .resultText(resources.get(0).content())
                .build();
    }

    @Override
    public String execute(ToolExecutionRequest request, Object memoryId) {
        throw new IllegalStateException("executeWithContext must be called instead");
    }
}