package dev.langchain4j.skills;

import dev.langchain4j.Experimental;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.service.tool.AiServiceTool;
import dev.langchain4j.service.tool.ToolExecutor;
import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.service.tool.ToolProviderResult;
import dev.langchain4j.service.tool.ToolService;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static java.util.Arrays.asList;

@Experimental
public abstract class AbstractSkill implements Skill {

    private final String name;
    private final String description;
    private final String content;
    private final List<SkillResource> resources;
    private final List<ToolProvider> toolProviders;

    protected AbstractSkill(BaseBuilder<?> builder) {
        this.name = ensureNotBlank(builder.name, "name");
        this.description = ensureNotBlank(builder.description, "description");
        this.content = ensureNotBlank(builder.content, "content");
        this.resources = copy(builder.resources);
        validateUniquePaths(this.resources);
        this.toolProviders = buildToolProviders(builder);
    }

    private static List<ToolProvider> buildToolProviders(BaseBuilder<?> builder) {
        List<AiServiceTool> allTools = new ArrayList<>();
        if (builder.annotatedTools != null) {
            allTools.addAll(builder.annotatedTools);
        }
        if (builder.mapTools != null) {
            allTools.addAll(builder.mapTools);
        }

        List<ToolProvider> result = new ArrayList<>();

        if (!allTools.isEmpty()) {
            Map<ToolSpecification, ToolExecutor> tools = new HashMap<>();
            Set<String> immediateReturnToolNames = new HashSet<>();
            for (AiServiceTool tool : allTools) {
                tools.put(tool.toolSpecification(), tool.toolExecutor());
                if (tool.immediateReturn()) {
                    immediateReturnToolNames.add(tool.toolSpecification().name());
                }
            }
            ToolProviderResult staticResult = ToolProviderResult.builder()
                    .addAll(tools)
                    .immediateReturnToolNames(immediateReturnToolNames)
                    .build();
            result.add(request -> staticResult);
        }

        if (builder.toolProviders != null) {
            result.addAll(builder.toolProviders);
        }

        return result;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public String description() {
        return description;
    }

    @Override
    public String content() {
        return content;
    }

    @Override
    public List<SkillResource> resources() {
        return resources;
    }

    @Override
    public List<ToolProvider> toolProviders() {
        return toolProviders;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AbstractSkill that)) return false;
        return Objects.equals(name, that.name)
                && Objects.equals(description, that.description)
                && Objects.equals(content, that.content)
                && Objects.equals(resources, that.resources)
                && Objects.equals(toolProviders, that.toolProviders);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, description, content, resources, toolProviders);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " {"
                + " name = " + name
                + ", description = " + description
                + ", content = " + content
                + ", resources = " + resources
                + ", toolProviders = " + toolProviders
                + " }";
    }

    private static void validateUniquePaths(List<SkillResource> resources) {
        Set<String> seenPaths = new HashSet<>();
        for (SkillResource resource : resources) {
            String path = resource.relativePath();
            if (!seenPaths.add(path)) {
                throw new IllegalStateException(
                        "Duplicate skill resource path detected: '%s'".formatted(path));
            }
        }
    }

    @SuppressWarnings("unchecked")
    public abstract static class BaseBuilder<B extends BaseBuilder<B>> {

        private String name;
        private String description;
        private String content;
        private Collection<? extends SkillResource> resources;
        private List<AiServiceTool> annotatedTools;
        private List<ToolProvider> toolProviders;
        private List<AiServiceTool> mapTools;

        public B name(String name) {
            this.name = name;
            return (B) this;
        }

        public B description(String description) {
            this.description = description;
            return (B) this;
        }

        public B content(String content) {
            this.content = content;
            return (B) this;
        }

        public B resources(Collection<? extends SkillResource> resources) {
            this.resources = resources;
            return (B) this;
        }

        public B tools(Object... objectsWithTools) {
            this.annotatedTools = new ArrayList<>();
            for (Object object : objectsWithTools) {
                this.annotatedTools.addAll(ToolService.findTools(object));
            }
            return (B) this;
        }

        public B toolProviders(Collection<? extends ToolProvider> toolProviders) {
            this.toolProviders = new ArrayList<>(toolProviders);
            return (B) this;
        }

        public B toolProviders(ToolProvider... toolProviders) {
            return toolProviders(asList(toolProviders));
        }

        public B tools(Map<ToolSpecification, ToolExecutor> tools) {
            this.mapTools = new ArrayList<>();
            for (Map.Entry<ToolSpecification, ToolExecutor> entry : tools.entrySet()) {
                this.mapTools.add(AiServiceTool.builder()
                        .toolSpecification(entry.getKey())
                        .toolExecutor(entry.getValue())
                        .build());
            }
            return (B) this;
        }
    }
}
