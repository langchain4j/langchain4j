package dev.langchain4j.agentic.supervisor;

/**
 * Strategy to decide which responses the supervisor agent should return.
 */
public enum SupervisorResponseStrategy {
    /**
     * Use an internal LLM to score the last responses and the summarization of the interaction of the supervisor
     * with its sub-agents against the original user request, and return the one with the higher score.
     */
    SCORED,
    /**
     * Return a summarization of the interaction of the supervisor with its sub-agents.
     */
    SUMMARY,
    /**
     * Return only the final responses of the last invoked sub-agent (default).
     */
    LAST
}
