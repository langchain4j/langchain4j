package dev.langchain4j.agentic.supervisor;

import dev.langchain4j.agentic.declarative.ChatModelSupplier;
import dev.langchain4j.model.chat.ChatModel;

public interface AgentWithParameterizedSupplier extends SupervisorAgent {

    @ChatModelSupplier
    static ChatModel chatModel(ChatModel selector) {
        return selector;
    }
}
