package dev.langchain4j.model.spark.spi;

import dev.langchain4j.model.spark.SparkChatModel;

import java.util.function.Supplier;

/**
 * @author ren
 * @since 2024/3/15 11:09
 */
public interface SparkChatModelBuilderFactory extends Supplier<SparkChatModel.SparkChatModelBuilder> {
}
