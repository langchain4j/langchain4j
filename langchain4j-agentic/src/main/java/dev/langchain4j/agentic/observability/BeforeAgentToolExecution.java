package dev.langchain4j.agentic.observability;

import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.service.tool.BeforeToolExecution;

public record BeforeAgentToolExecution(AgenticScope agenticScope, AgentInstance agentInstance, BeforeToolExecution toolExecution) {
}
