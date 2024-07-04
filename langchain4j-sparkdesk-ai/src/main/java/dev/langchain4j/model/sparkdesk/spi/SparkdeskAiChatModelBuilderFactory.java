package dev.langchain4j.model.sparkdesk.spi;

import dev.langchain4j.model.sparkdesk.SparkdeskAiChatModel;

import java.util.function.Supplier;

/**
 * A factory for building {@link SparkdeskAiChatModel.SparkdeskAiChatModelBuilder} instances.
 */
public interface SparkdeskAiChatModelBuilderFactory extends Supplier<SparkdeskAiChatModel.SparkdeskAiChatModelBuilder> {
}
