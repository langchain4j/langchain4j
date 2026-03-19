package dev.langchain4j.model.minimax.spi;

import dev.langchain4j.model.minimax.MiniMaxChatModel;

import java.util.function.Supplier;

/**
 * A factory for building {@link MiniMaxChatModel.MiniMaxChatModelBuilder} instances.
 */
public interface MiniMaxChatModelBuilderFactory extends Supplier<MiniMaxChatModel.MiniMaxChatModelBuilder> {
}
