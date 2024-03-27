package dev.langchain4j.model.spark.spi;

import dev.langchain4j.model.spark.SparkStreamingLanguageModel;

import java.util.function.Supplier;

/**
 * @author ren
 * @since 2024/3/15 16:28
 */
public interface SparkStreamingLanguageModelBuilderFactory extends Supplier<SparkStreamingLanguageModel.SparkStreamingLanguageModelBuilder> {
}
