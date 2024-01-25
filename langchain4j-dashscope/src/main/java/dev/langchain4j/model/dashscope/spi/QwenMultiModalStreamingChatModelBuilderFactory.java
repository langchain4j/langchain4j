package dev.langchain4j.model.dashscope.spi;

import dev.langchain4j.model.dashscope.QwenMultiModalChatModel;
import dev.langchain4j.model.dashscope.QwenMultiModalStreamingChatModel;

import java.util.function.Supplier;

/**
 * A factory for building {@link QwenMultiModalStreamingChatModel.QwenMultiModalStreamingChatModelBuilder} instances.
 */
public interface QwenMultiModalStreamingChatModelBuilderFactory extends Supplier<QwenMultiModalStreamingChatModel.QwenMultiModalStreamingChatModelBuilder> {
}
