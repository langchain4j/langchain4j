package dev.langchain4j.model.batch;

import dev.langchain4j.Experimental;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;

/**
 * A batch model for processing multiple chat requests asynchronously.
 */
@Experimental
public interface BatchChatModel extends BatchModel<ChatRequest, ChatResponse> {}
