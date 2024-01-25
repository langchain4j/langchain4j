package dev.langchain4j.model.chatglm.spi;

import dev.langchain4j.model.chatglm.ChatGlmChatModel;

import java.util.function.Supplier;

/**
 * A factory for building {@link ChatGlmChatModel.ChatGlmChatModelBuilder} instances.
 */
public interface ChatGlmChatModelBuilderFactory extends Supplier<ChatGlmChatModel.ChatGlmChatModelBuilder> {
}
