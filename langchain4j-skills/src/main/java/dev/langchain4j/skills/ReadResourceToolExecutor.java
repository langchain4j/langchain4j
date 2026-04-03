package dev.langchain4j.skills;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.service.tool.ToolExecutionResult;

import java.util.List;
import java.util.Map;

import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static java.util.stream.Collectors.joining;

class ReadResourceToolExecutor extends AbstractSkillToolExecutor {

    private final ReadResourceToolConfig config;
    private final Map<String, Skill> skillsByName;

    ReadResourceToolExecutor(ReadResourceToolConfig config, Map<String, Skill> skillsByName) {
        super(config.throwToolArgumentsExceptions);
        this.config = ensureNotNull(config, "config");
        this.skillsByName = copy(skillsByName);
    }

    @Override
    public ToolExecutionResult executeWithContext(ToolExecutionRequest request, InvocationContext context) {

        Map<String, Object> arguments = parseArguments(request.arguments());
        String skillName = getRequiredArgument(config.skillNameParameterName, arguments);
        String relativePath = getRequiredArgument(config.relativePathParameterName, arguments);

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
            throwException(("There is no resource for skill '%s' with the path '%s'. " +
                    "Available resources: [%s]").formatted(skillName, relativePath, availableResources));
        }

        SkillResource resource = resources.get(0);

        return ToolExecutionResult.builder()
                .result(resource)
                .resultText(resource.content())
                .build();
    }
}
