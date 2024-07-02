package dev.langchain4j.model.mistralai.spi;

import dev.langchain4j.model.mistralai.MistralAiStreamingChatModel;

import java.util.function.Supplier;

/**
 * A factory for building {@link MistralAiStreamingChatModel.MistralAiStreamingChatModelBuilder} instances.
 */
public interface MistralAiStreamingChatModelBuilderFactory extends Supplier<MistralAiStreamingChatModel.MistralAiStreamingChatModelBuilder> {
}
