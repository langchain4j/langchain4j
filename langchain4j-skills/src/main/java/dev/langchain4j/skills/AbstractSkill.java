package dev.langchain4j.skills;

import dev.langchain4j.Experimental;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.service.tool.ToolExecutor;
import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.service.tool.ToolProviderResult;
import dev.langchain4j.service.tool.ToolService;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;

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
        this.toolProviders = buildToolProviders(builder);
        validateUniquePaths(this.resources);
    }

    private static List<ToolProvider> buildToolProviders(BaseBuilder<?> builder) {
        List<ToolProvider> result = new ArrayList<>();
        if (builder.staticToolsResult != null && !builder.staticToolsResult.tools().isEmpty()) {
            result.add(request -> builder.staticToolsResult);
        }
        if (builder.toolProviders != null) {
            result.addAll(builder.toolProviders);
        }
        return List.copyOf(result);
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
        ToolProviderResult staticToolsResult;
        List<ToolProvider> toolProviders;

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

        public B resources(Collection<? extends SkillResource> resources) { // TODO also append?
            this.resources = resources;
            return (B) this;
        }

        /**
         * Sets the static tools for this skill from a map of tool specifications to executors.
         * These will be wrapped into a {@link ToolProvider} and combined with any TODO
         * {@linkplain #toolProviders(ToolProvider...) external tool providers}.
         */
        public B tools(Map<ToolSpecification, ToolExecutor> tools) {
            ToolProviderResult newResult = ToolProviderResult.builder()
                    .addAll(tools)
                    .build();
            this.staticToolsResult = merge(this.staticToolsResult, newResult);
            return (B) this;
        }

        /**
         * Sets the static tools for this skill from objects containing
         * {@link dev.langchain4j.agent.tool.Tool @Tool}-annotated methods.
         * These will be wrapped into a {@link ToolProvider} and combined with any TODO
         * {@linkplain #toolProviders(ToolProvider...) external tool providers}.
         *
         * @param objectsWithTools objects with {@link dev.langchain4j.agent.tool.Tool @Tool}-annotated methods
         */
        public B tools(Object... objectsWithTools) {
            ToolProviderResult newResult = ToolService.toToolProviderResult(objectsWithTools);
            this.staticToolsResult = merge(this.staticToolsResult, newResult);
            return (B) this;
        }

        private static ToolProviderResult merge(ToolProviderResult existing, ToolProviderResult addition) {
            if (existing == null) {
                return addition;
            }
            return existing.toBuilder()
                    .addAll(addition.tools())
                    .immediateReturnToolNames(addition.immediateReturnToolNames())
                    .build();
        }

        /**
         * Sets the tool providers for this skill (e.g. MCP tool providers).
         * These will be called each time the AI service is invoked. TODO
         * <p>
         * Can be combined with {@link #tools(Map)} or {@link #tools(Object...)} —
         * static tools and providers are merged into a single list.
         */
        public B toolProviders(ToolProvider... toolProviders) {
            if (this.toolProviders == null) {
                this.toolProviders = new ArrayList<>();
            }
            this.toolProviders.addAll(List.of(toolProviders));
            return (B) this;
        }

        /**
         * Sets the tool providers for this skill (e.g. MCP tool providers).
         * These will be called each time the AI service is invoked. TODO
         * <p>
         * Can be combined with {@link #tools(Map)} or {@link #tools(Object...)} —
         * static tools and providers are merged into a single list.
         */
        public B toolProviders(Collection<ToolProvider> toolProviders) {
            if (this.toolProviders == null) {
                this.toolProviders = new ArrayList<>();
            }
            this.toolProviders.addAll(toolProviders);
            return (B) this;
        }
    }
}
