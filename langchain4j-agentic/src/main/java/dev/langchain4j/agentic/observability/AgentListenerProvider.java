package dev.langchain4j.agentic.observability;

import dev.langchain4j.Internal;

@Internal
public interface AgentListenerProvider {
    AgenticListener listener();
}
