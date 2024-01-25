package dev.langchain4j.model.dashscope.spi;

import dev.langchain4j.model.dashscope.QwenMultiModalChatModel;

import java.util.function.Supplier;

/**
 * A factory for building {@link QwenMultiModalChatModel.QwenMultiModalChatModelBuilder} instances.
 */
public interface QwenMultiModalChatModelBuilderFactory extends Supplier<QwenMultiModalChatModel.QwenMultiModalChatModelBuilder> {
}
