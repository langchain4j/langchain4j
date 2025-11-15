package dev.langchain4j.agentic.planner;

import dev.langchain4j.agentic.scope.AgenticScope;
import java.util.List;

public record InitPlanningContext(
        AgenticScope agenticScope, AgentInstance plannerAgent, List<AgentInstance> subagents) {}
