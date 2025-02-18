package dev.langchain4j.model.chat;

import dev.langchain4j.Experimental;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.util.function.Supplier;

/**
 * Generic executor interface that defines a chat interaction
 */
@Experimental
@FunctionalInterface
public interface ChatExecutor extends Supplier<ChatResponse> {}
