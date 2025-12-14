package dev.langchain4j.service.tool;

import static dev.langchain4j.internal.Utils.copy;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.service.IllegalConfigurationException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class ToolProviderResult {

    private final Map<ToolSpecification, ToolExecutor> tools;
    private final Map<String, ToolSpecification> toolsByName;
    private final Set<String> immediateReturnToolsByName;

    public ToolProviderResult(Map<ToolSpecification, ToolExecutor> tools) {
        this(tools, indexTools(tools));
    }

    public ToolProviderResult(Map<ToolSpecification, ToolExecutor> tools, Set<String> immediateReturnToolNames) {
        this(tools, indexTools(tools), immediateReturnToolNames);
    }

    private ToolProviderResult(Map<ToolSpecification, ToolExecutor> tools, Map<String, ToolSpecification> toolsByName) {
        this(tools, toolsByName, new HashSet<>());
    }

    private ToolProviderResult(
            Map<ToolSpecification, ToolExecutor> tools,
            Map<String, ToolSpecification> toolsByName,
            Set<String> immediateReturnToolsByName) {
        this.tools = copy(tools);
        this.toolsByName = copy(toolsByName);
        this.immediateReturnToolsByName = copy(immediateReturnToolsByName);
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

    public Set<String> immediateReturnToolNames() {
        return immediateReturnToolsByName;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private final Map<ToolSpecification, ToolExecutor> tools = new HashMap<>();
        private final Map<String, ToolSpecification> toolsByName = new HashMap<>();
        private final Set<String> immediateReturnToolsByName = new HashSet<>();
        private ToolSpecification lastAddedTool;

        public Builder add(ToolSpecification tool, ToolExecutor executor) {
            tools.put(tool, executor);
            if (toolsByName.putIfAbsent(tool.name(), tool) != null) {
                throw new IllegalConfigurationException("Duplicated definition for tool: " + tool.name());
            }
            lastAddedTool = tool;
            return this;
        }

        public Builder withImmediateReturn() {
            if (lastAddedTool == null) {
                throw new IllegalStateException("No tool has been added yet. Call add() before withImmediateReturn()");
            }
            immediateReturnToolsByName.add(lastAddedTool.name());
            lastAddedTool = null; // Clear to prevent accidental reuse
            return this;
        }

        public Builder withImmediateReturn(Set<String> toolNames) {
            immediateReturnToolsByName.addAll(toolNames);
            return this;
        }

        public Builder addAll(Map<ToolSpecification, ToolExecutor> tools) {
            tools.forEach(this::add);
            lastAddedTool =
                    null; // Prevent withImmediateReturn() from applying to unpredictable tool from map iteration
            return this;
        }

        public ToolProviderResult build() {
            return new ToolProviderResult(tools, toolsByName, immediateReturnToolsByName);
        }
    }
}
