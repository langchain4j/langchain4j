package dev.langchain4j.service.tool;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.service.IllegalConfigurationException;

import java.util.HashMap;
import java.util.Map;

import static dev.langchain4j.internal.Utils.copy;

public class ToolProviderResult {

    private final Map<ToolSpecification, ToolExecutor> tools;
    private final Map<String, ToolSpecification> toolsByName;

    public ToolProviderResult(Map<ToolSpecification, ToolExecutor> tools) {
        this(tools, indexTools(tools));
    }

    private ToolProviderResult(Map<ToolSpecification, ToolExecutor> tools, Map<String, ToolSpecification> toolsByName) {
        this.tools = copy(tools);
        this.toolsByName = copy(toolsByName);
    }

    private static Map<String, ToolSpecification> indexTools(Map<ToolSpecification, ToolExecutor> tools) {
        Map<String, ToolSpecification> toolsByName = new HashMap<>();
        tools.keySet().forEach(toolSpecification -> {
            if (toolsByName.putIfAbsent(toolSpecification.name(), toolSpecification) != null) {
                throw new IllegalConfigurationException("Duplicated definition for tool: " + toolSpecification.name());
            }
        });
        return toolsByName;
    }

    public ToolSpecification toolSpecificationByName(String name) {
        return toolsByName.get(name);
    }

    public ToolExecutor toolExecutorByName(String name) {
        ToolSpecification toolSpecification = toolSpecificationByName(name);
        return toolSpecification == null ? null : tools.get(toolSpecification);
    }

    public Map<ToolSpecification, ToolExecutor> tools() {
        return tools;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private final Map<ToolSpecification, ToolExecutor> tools = new HashMap<>();
        private final Map<String, ToolSpecification> toolsByName = new HashMap<>();

        public Builder add(ToolSpecification tool, ToolExecutor executor) {
            tools.put(tool, executor);
            if (toolsByName.putIfAbsent(tool.name(), tool) != null) {
                throw new IllegalConfigurationException("Duplicated definition for tool: " + tool.name());
            }
            return this;
        }

        public Builder addAll(Map<ToolSpecification, ToolExecutor> tools) {
            tools.forEach(this::add);
            return this;
        }

        public ToolProviderResult build() {
            return new ToolProviderResult(tools, toolsByName);
        }
    }
}
