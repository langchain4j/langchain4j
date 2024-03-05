package dev.langchain4j.model.qianfan.spi;



import dev.langchain4j.model.qianfan.QianfanStreamingLanguageModel;

import java.util.function.Supplier;

/**
 * A factory for building {@link QianfanStreamingLanguageModel.QianfanStreamingLanguageModelBuilder} instances.
 */
public interface QianfanStreamingLanguageModelBuilderFactory extends Supplier<QianfanStreamingLanguageModel.QianfanStreamingLanguageModelBuilder> {
}
