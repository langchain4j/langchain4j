package dev.langchain4j.agentic.planner;

import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.service.memory.ChatMemoryAccess;

public interface ChatMemoryAccessProvider {
    ChatMemoryAccess chatMemoryAccess(AgenticScope agenticScope);
}
