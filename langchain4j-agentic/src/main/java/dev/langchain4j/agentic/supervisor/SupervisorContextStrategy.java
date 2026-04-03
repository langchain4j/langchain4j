package dev.langchain4j.agentic.supervisor;

import dev.langchain4j.memory.ChatMemory;

/**
 * Strategy for providing context to the supervisor agent.
 */
public enum SupervisorContextStrategy {
    /**
     * Use only the supervisors {@link ChatMemory} (default).
     */
    CHAT_MEMORY,
    /**
     * Use only a summarization of the interaction of the supervisor with its sub-agents.
     */
    SUMMARIZATION,
    /**
     * Use both the supervisor's {@link ChatMemory} and a summarization of the interaction of the supervisor with its sub-agents.
     */
    CHAT_MEMORY_AND_SUMMARIZATION
}
