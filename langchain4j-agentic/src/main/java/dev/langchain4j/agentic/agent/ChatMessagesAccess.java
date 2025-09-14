package dev.langchain4j.agentic.agent;

import dev.langchain4j.data.message.UserMessage;

public interface ChatMessagesAccess {
    UserMessage lastUserMessage();
}
