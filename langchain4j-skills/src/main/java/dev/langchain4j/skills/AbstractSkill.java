package dev.langchain4j.skills;

import dev.langchain4j.Experimental;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.service.tool.DefaultToolExecutor;
import dev.langchain4j.service.tool.ToolExecutor;
import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.service.tool.ToolProviderResult;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static dev.langchain4j.agent.tool.ToolSpecifications.toolSpecificationFrom;
import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.Utils.getAnnotatedMethod;
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
        if (builder.staticTools != null && !builder.staticTools.isEmpty()) {
            ToolProviderResult staticResult = ToolProviderResult.builder()
                    .addAll(builder.staticTools)
                    .build();
            result.add(request -> staticResult);
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
        Map<ToolSpecification, ToolExecutor> staticTools;
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

        public B resources(Collection<? extends SkillResource> resources) {
            this.resources = resources;
            return (B) this;
        }

        /**
         * Sets the static tools for this skill from a map of tool specifications to executors.
         * These will be wrapped into a {@link ToolProvider} and combined with any
         * {@linkplain #toolProviders(ToolProvider...) external tool providers}.
         */
        public B tools(Map<ToolSpecification, ToolExecutor> tools) {
            this.staticTools = tools; // TODO append
            return (B) this;
        }

        /**
         * Sets the static tools for this skill from objects containing {@link Tool @Tool}-annotated methods.
         * These will be wrapped into a {@link ToolProvider} and combined with any
         * {@linkplain #toolProviders(ToolProvider...) external tool providers}.
         *
         * @param objectsWithTools objects with {@link Tool @Tool}-annotated methods
         */
        public B tools(Object... objectsWithTools) { // TODO extract common logic?
            Map<ToolSpecification, ToolExecutor> toolMap = new LinkedHashMap<>();
            for (Object object : objectsWithTools) {
                for (Method method : object.getClass().getDeclaredMethods()) {
                    getAnnotatedMethod(method, Tool.class).ifPresent(toolMethod -> {
                        ToolSpecification spec = toolSpecificationFrom(toolMethod);
                        ToolExecutor executor = DefaultToolExecutor.builder()
                                .object(object)
                                .originalMethod(toolMethod)
                                .methodToInvoke(toolMethod)
                                .wrapToolArgumentsExceptions(true)
                                .propagateToolExecutionExceptions(true)
                                .build();
                        toolMap.put(spec, executor);
                    });
                }
            }
            this.staticTools = toolMap; // TODO append
            return (B) this;
        }

        /**
         * Sets the external tool providers for this skill (e.g. MCP tool providers).
         * These will be called each time the AI service is invoked.
         * <p>
         * Can be combined with {@link #tools(Map)} or {@link #tools(Object...)} —
         * static tools and external providers are merged into a single list.
         */
        public B toolProviders(ToolProvider... toolProviders) {
            this.toolProviders = List.of(toolProviders); // TODO append
            return (B) this;
        }

        /**
         * Sets the external tool providers for this skill (e.g. MCP tool providers).
         * These will be called each time the AI service is invoked.
         * <p>
         * Can be combined with {@link #tools(Map)} or {@link #tools(Object...)} —
         * static tools and external providers are merged into a single list.
         */
        public B toolProviders(Collection<ToolProvider> toolProviders) {
            this.toolProviders = new ArrayList<>(toolProviders); // TODO append
            return (B) this;
        }
    }
}
