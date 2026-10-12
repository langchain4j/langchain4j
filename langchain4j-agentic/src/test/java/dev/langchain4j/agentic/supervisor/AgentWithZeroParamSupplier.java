package dev.langchain4j.agentic.supervisor;

import static org.mockito.Mockito.mock;

import dev.langchain4j.agentic.declarative.ChatModelSupplier;
import dev.langchain4j.model.chat.ChatModel;

public interface AgentWithZeroParamSupplier extends SupervisorAgent {
    ChatModel SUPPLIED_MODEL = mock(ChatModel.class);

    @ChatModelSupplier
    static ChatModel chatModel() {
        return SUPPLIED_MODEL;
    }
}
