package dev.langchain4j.model.spark.chat.spi;


import dev.langchain4j.model.spark.chat.SparkChatModel;

import java.util.function.Supplier;

/**
 * A factory for building  instances.
 */
public interface SparkChatModelBuilderFactory extends Supplier<SparkChatModel.SparkChatModelBuilder> {
}
