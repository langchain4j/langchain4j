package dev.langchain4j.agentic.workflow;

import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.scope.AgenticScope;
import java.util.List;
import java.util.function.Predicate;

public record ConditionalAgent(String condition, Predicate<AgenticScope> predicate, List<AgentInstance> agentInstances) { }
