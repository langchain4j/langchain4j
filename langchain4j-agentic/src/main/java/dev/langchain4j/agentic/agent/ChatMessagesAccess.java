package dev.langchain4j.agentic.agent;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;

public interface ChatMessagesAccess {

    UserMessage lastUserMessage(Object agenticScopeId);

    ChatRequest lastChatRequest(Object agenticScopeId);

    ChatResponse lastChatResponse(Object agenticScopeId);

    void removeLast(Object agenticScopeId);
}
