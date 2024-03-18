package dev.langchain4j.model.spark.spi;

import dev.langchain4j.model.spark.SparkStreamingChatModel;

import java.util.function.Supplier;

/**
 * @author ren
 * @since 2024/3/15 16:28
 */
public interface SparkStreamingChatModelBuilderFactory extends Supplier<SparkStreamingChatModel.SparkStreamingChatModelBuilder> {
}
