package dev.langchain4j.service.tool;

import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import java.util.List;

public record ToolExecutionResult(
        ChatResponse chatResponse, List<ToolExecution> toolExecutions, TokenUsage tokenUsageAccumulator) {}
