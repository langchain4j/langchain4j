package dev.langchain4j.model.minimax.spi;

import dev.langchain4j.model.minimax.MiniMaxStreamingChatModel;

import java.util.function.Supplier;

/**
 * A factory for building {@link MiniMaxStreamingChatModel.MiniMaxStreamingChatModelBuilder} instances.
 */
public interface MiniMaxStreamingChatModelBuilderFactory extends Supplier<MiniMaxStreamingChatModel.MiniMaxStreamingChatModelBuilder> {
}
