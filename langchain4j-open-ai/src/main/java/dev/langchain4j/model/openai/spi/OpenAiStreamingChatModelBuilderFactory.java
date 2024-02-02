package dev.langchain4j.model.openai.spi;

import dev.langchain4j.model.openai.OpenAiStreamingChatModel;

import java.util.function.Supplier;

/**
 * A factory for building {@link OpenAiStreamingChatModel.OpenAiStreamingChatModelBuilder} instances.
 */
public interface OpenAiStreamingChatModelBuilderFactory extends Supplier<OpenAiStreamingChatModel.OpenAiStreamingChatModelBuilder> {
}
