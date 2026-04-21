package dev.langchain4j.service.tool;

import static dev.langchain4j.internal.Utils.copy;

import dev.langchain4j.agent.tool.ReturnBehavior;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.service.IllegalConfigurationException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class ToolProviderResult {

    private final Map<ToolSpecification, ToolExecutor> tools;
    private final Map<String, ToolSpecification> toolsByName;
    private final Set<String> immediateReturnToolNames;
    private final Set<String> immediateIfLastReturnToolNames;

    public ToolProviderResult(Builder builder) {
        this(
                builder.tools,
                builder.toolsByName,
                builder.immediateReturnToolNames,
                builder.immediateIfLastReturnToolNames);
    }

    public ToolProviderResult(Map<ToolSpecification, ToolExecutor> tools) {
        this(tools, indexTools(tools), Set.of(), Set.of());
    }

    private ToolProviderResult(
            Map<ToolSpecification, ToolExecutor> tools,
            Map<String, ToolSpecification> toolsByName,
            Set<String> immediateReturnToolNames,
            Set<String> immediateIfLastReturnToolNames) {
        this.tools = copy(tools);
        this.toolsByName = copy(toolsByName);
        this.immediateReturnToolNames = copy(immediateReturnToolNames);
        this.immediateIfLastReturnToolNames = copy(immediateIfLastReturnToolNames);
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
        return immediateReturnToolNames;
    }

    public Set<String> immediateIfLastReturnToolNames() {
        return immediateIfLastReturnToolNames;
    }

    public Builder toBuilder() {
        return builder()
                .addAll(tools)
                .immediateReturnToolNames(immediateReturnToolNames)
                .immediateIfLastReturnToolNames(immediateIfLastReturnToolNames);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private final Map<ToolSpecification, ToolExecutor> tools = new HashMap<>();
        private final Map<String, ToolSpecification> toolsByName = new HashMap<>();
        private final Set<String> immediateReturnToolNames = new HashSet<>();
        private final Set<String> immediateIfLastReturnToolNames = new HashSet<>();

        public Builder add(ToolSpecification tool, ToolExecutor executor) {
            return add(tool, executor, ReturnBehavior.TO_LLM);
        }

        public Builder add(ToolSpecification tool, ToolExecutor executor, ReturnBehavior returnBehavior) {
            tools.put(tool, executor);
            if (toolsByName.putIfAbsent(tool.name(), tool) != null) {
                throw new IllegalConfigurationException("Duplicated definition for tool: " + tool.name());
            }
            if (returnBehavior == ReturnBehavior.IMMEDIATE) {
                immediateReturnToolNames.add(tool.name());
            } else if (returnBehavior == ReturnBehavior.IMMEDIATE_IF_LAST) {
                immediateIfLastReturnToolNames.add(tool.name());
            }
            return this;
        }

        public Builder addAll(Map<ToolSpecification, ToolExecutor> tools) {
            tools.forEach(this::add);
            return this;
        }

        public Builder immediateReturnToolNames(Set<String> immediateReturnToolNames) {
            if (immediateReturnToolNames != null) {
                this.immediateReturnToolNames.addAll(immediateReturnToolNames);
            }
            return this;
        }

        public Builder immediateIfLastReturnToolNames(Set<String> immediateIfLastReturnToolNames) {
            if (immediateIfLastReturnToolNames != null) {
                this.immediateIfLastReturnToolNames.addAll(immediateIfLastReturnToolNames);
            }
            return this;
        }

        public ToolProviderResult build() {
            return new ToolProviderResult(this);
        }
    }
}
