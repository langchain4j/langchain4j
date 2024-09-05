package dev.langchain4j.service.tool;

import dev.langchain4j.agent.tool.ToolSpecification;

import java.util.LinkedHashMap;
import java.util.Map;

import static dev.langchain4j.internal.Utils.copyIfNotNull;

public class ToolProviderResult {

    private final Map<ToolSpecification, ToolExecutor> tools;

    public ToolProviderResult(Map<ToolSpecification, ToolExecutor> tools) {
        this.tools = copyIfNotNull(tools);
    }

    public Map<ToolSpecification, ToolExecutor> tools() {
        return tools;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private final Map<ToolSpecification, ToolExecutor> tools = new LinkedHashMap<>();

        public Builder add(ToolSpecification tool, ToolExecutor executor) {
            tools.put(tool, executor);
            return this;
        }

        public Builder addAll(Map<ToolSpecification, ToolExecutor> tools) {
            this.tools.putAll(tools);
            return this;
        }

        public ToolProviderResult build() {
            return new ToolProviderResult(tools);
        }
    }
}