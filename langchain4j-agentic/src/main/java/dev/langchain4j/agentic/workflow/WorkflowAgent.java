package dev.langchain4j.agentic.workflow;

import dev.langchain4j.agentic.AgentInstance;
import dev.langchain4j.memory.ChatMemory;
import java.util.Map;

public interface WorkflowAgent extends AgentInstance {

    String WORKFLOW_AGENT_SUFFIX = "_WORKFLOW_AGENT";

    Map<String, Object> invoke(Map<String, Object> state);

    default void setChatMemory(ChatMemory chatMemory) {
        throw new UnsupportedOperationException();
    }

    default String outputName() {
        return null;
    }
}
