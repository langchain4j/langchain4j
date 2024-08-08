package dev.langchain4j.service.tool;

import dev.langchain4j.agent.tool.ToolSpecification;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * A wrapper mostly used by the {@link ToolProvider}
 */
public class ToolProviderResult {
    private final Map<ToolSpecification, ToolExecutor> tools = new HashMap<>();

    public Map<ToolSpecification, ToolExecutor> getTools() {
        return Collections.unmodifiableMap(tools);
    }

    public void add(ToolSpecification specification, ToolExecutor toolExecutor) {
        this.tools.put(specification, toolExecutor);
    }
}