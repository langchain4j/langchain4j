package dev.langchain4j.agentic.agent;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;

public interface ChatMessagesAccess {

    UserMessage lastUserMessage();

    ChatRequest lastChatRequest();

    ChatResponse lastChatResponse();
}
