package dev.langchain4j.service.tool;

import dev.langchain4j.agent.tool.ReturnBehavior;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.service.IllegalConfigurationException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static java.util.stream.Collectors.toSet;

public class ToolProviderResult {

    private final List<AiServiceTool> tools;

    public ToolProviderResult(Builder builder) {
        this.tools = builder.buildFinalToolList();
    }

    public ToolProviderResult(List<AiServiceTool> tools) {
        this(builder().addAll(tools));
    }

    public ToolProviderResult(Map<ToolSpecification, ToolExecutor> tools) {
        this(builder().addAll(tools));
    }

    /**
     * @since 1.14.0
     */
    public List<AiServiceTool> aiServiceTools() {
        return tools;
    }

    @Deprecated(since = "1.14.0")
    public ToolSpecification toolSpecificationByName(String name) {
        for (AiServiceTool tool : tools) {
            if (tool.name().equals(name)) {
                return tool.toolSpecification();
            }
        }
        return null;
    }

    @Deprecated(since = "1.14.0")
    public ToolExecutor toolExecutorByName(String name) {
        for (AiServiceTool tool : tools) {
            if (tool.name().equals(name)) {
                return tool.toolExecutor();
            }
        }
        return null;
    }

    /**
     * @deprecated use {@link #aiServiceTools()} instead
     */
    @Deprecated(since = "1.14.0")
    public Map<ToolSpecification, ToolExecutor> tools() {
        Map<ToolSpecification, ToolExecutor> result = new LinkedHashMap<>(tools.size());
        for (AiServiceTool tool : tools) {
            result.put(tool.toolSpecification(), tool.toolExecutor());
        }
        return result;
    }

    /**
     * @deprecated use {@link #aiServiceTools()} to get tool's {@link ReturnBehavior}
     */
    @Deprecated(since = "1.14.0")
    public Set<String> immediateReturnToolNames() {
        return tools.stream()
                .filter(tool -> tool.returnBehavior() == ReturnBehavior.IMMEDIATE)
                .map(tool -> tool.name())
                .collect(toSet());
    }

    public Builder toBuilder() {
        return builder().addAll(tools);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private final List<AiServiceTool> tools = new ArrayList<>();
        private final Set<String> immediateReturnToolNames = new HashSet<>();

        public Builder add(AiServiceTool tool) {
            tools.add(tool);
            return this;
        }

        public Builder add(ToolSpecification tool, ToolExecutor executor) {
            return add(tool, executor, ReturnBehavior.TO_LLM);
        }

        public Builder add(ToolSpecification tool, ToolExecutor executor, ReturnBehavior returnBehavior) {
            tools.add(AiServiceTool.builder()
                    .toolSpecification(tool)
                    .toolExecutor(executor)
                    .returnBehavior(returnBehavior)
                    .build());
            return this;
        }

        public Builder addAll(Collection<AiServiceTool> tools) {
            tools.forEach(this::add);
            return this;
        }

        public Builder addAll(Map<ToolSpecification, ToolExecutor> tools) {
            tools.forEach(this::add);
            return this;
        }

        /**
         * @deprecated use {@link #add(AiServiceTool)} or {@link #add(ToolSpecification, ToolExecutor, ReturnBehavior)}
         * to specify tool's {@link ReturnBehavior}
         */
        @Deprecated(since = "1.14.0")
        public Builder immediateReturnToolNames(Set<String> immediateReturnToolNames) {
            if (immediateReturnToolNames != null) {
                this.immediateReturnToolNames.addAll(immediateReturnToolNames);
            }
            return this;
        }

        public ToolProviderResult build() {
            return new ToolProviderResult(this);
        }

        private List<AiServiceTool> buildFinalToolList() {
            Map<String, Integer> toolsByName = new HashMap<>(tools.size());
            for (int i = 0; i < tools.size(); i++) {
                String name = tools.get(i).name();
                if (toolsByName.putIfAbsent(name, i) != null) {
                    throw new IllegalConfigurationException("Duplicated definition for tool: " + name);
                }
            }
            for (String name : immediateReturnToolNames) {
                Integer idx = toolsByName.get(name);
                if (idx == null) {
                    continue;
                }
                AiServiceTool existing = tools.get(idx);
                if (existing.returnBehavior() != ReturnBehavior.IMMEDIATE) {
                    tools.set(idx, existing.toBuilder()
                            .returnBehavior(ReturnBehavior.IMMEDIATE)
                            .build());
                }
            }
            return tools;
        }
    }
}
