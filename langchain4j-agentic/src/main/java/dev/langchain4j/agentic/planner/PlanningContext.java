package dev.langchain4j.agentic.planner;

import dev.langchain4j.agentic.scope.AgentInvocation;
import dev.langchain4j.agentic.scope.AgenticScope;

public record PlanningContext(AgenticScope agenticScope, AgentInvocation previousAgentInvocation) {
}
