package dev.langchain4j.model.openai.spi;

import dev.langchain4j.Internal;
import dev.langchain4j.model.openai.OpenAiChatModel;

import java.util.function.Supplier;

/**
 * A factory for building {@link OpenAiChatModel.OpenAiChatModelBuilder} instances.
 */
@Internal
public interface OpenAiChatModelBuilderFactory extends Supplier<OpenAiChatModel.OpenAiChatModelBuilder> {
}
