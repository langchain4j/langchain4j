package dev.langchain4j.service.tool;

import dev.langchain4j.agent.tool.ToolSpecification;
import java.util.List;
import java.util.Map;

public record ToolExecutionContext(
        List<ToolSpecification> toolSpecifications, Map<String, ToolExecutor> toolExecutors) {}
