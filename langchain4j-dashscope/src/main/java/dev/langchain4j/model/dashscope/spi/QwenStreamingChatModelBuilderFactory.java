package dev.langchain4j.model.dashscope.spi;

import dev.langchain4j.model.dashscope.QwenStreamingChatModel;

import java.util.function.Supplier;

/**
 * A factory for building {@link QwenStreamingChatModel.QwenStreamingChatModelBuilder} instances.
 */
public interface QwenStreamingChatModelBuilderFactory extends Supplier<QwenStreamingChatModel.QwenStreamingChatModelBuilder> {
}
