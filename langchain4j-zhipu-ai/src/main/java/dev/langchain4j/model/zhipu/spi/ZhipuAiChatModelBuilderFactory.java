package dev.langchain4j.model.zhipu.spi;

import dev.langchain4j.model.zhipu.ZhipuAiChatModel;

import java.util.function.Supplier;

/**
 * A factory for building {@link ZhipuAiChatModel.ZhipuAiChatModelBuilder} instances.
 */
public interface ZhipuAiChatModelBuilderFactory extends Supplier<ZhipuAiChatModel.ZhipuAiChatModelBuilder> {
}
