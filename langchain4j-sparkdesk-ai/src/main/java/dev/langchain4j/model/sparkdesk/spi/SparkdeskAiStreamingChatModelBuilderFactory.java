package dev.langchain4j.model.sparkdesk.spi;

import dev.langchain4j.model.sparkdesk.SparkdeskAiStreamingChatModel;

import java.util.function.Supplier;

/**
 * A factory for building {@link SparkdeskAiStreamingChatModel.SparkdeskAiStreamingChatModelBuilder} instances.
 */
public interface SparkdeskAiStreamingChatModelBuilderFactory extends Supplier<SparkdeskAiStreamingChatModel.SparkdeskAiStreamingChatModelBuilder> {
}
