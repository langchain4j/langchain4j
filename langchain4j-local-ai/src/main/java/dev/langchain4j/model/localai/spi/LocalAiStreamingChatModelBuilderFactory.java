package dev.langchain4j.model.localai.spi;

import dev.langchain4j.model.localai.LocalAiStreamingChatModel;

import java.util.function.Supplier;

/**
 * A factory for building {@link LocalAiStreamingChatModel.LocalAiStreamingChatModelBuilder} instances.
 */
public interface LocalAiStreamingChatModelBuilderFactory extends Supplier<LocalAiStreamingChatModel.LocalAiStreamingChatModelBuilder> {
}
