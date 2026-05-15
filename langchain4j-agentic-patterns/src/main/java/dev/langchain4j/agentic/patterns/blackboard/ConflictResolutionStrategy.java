package dev.langchain4j.agentic.patterns.blackboard;

import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.scope.AgenticScope;

/**
 * Strategy for resolving conflicts when multiple agents can fire simultaneously on the blackboard.
 * The strategy receives the current scope state and two candidate agents, and returns which one
 * should be activated first. It is applied as a pairwise reduction over all ready agents.
 */
@FunctionalInterface
public interface ConflictResolutionStrategy {

    AgentInstance resolve(AgenticScope scope, AgentInstance a1, AgentInstance a2);
}
