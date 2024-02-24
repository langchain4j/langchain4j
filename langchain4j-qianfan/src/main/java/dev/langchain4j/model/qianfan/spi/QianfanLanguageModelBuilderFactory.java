package dev.langchain4j.model.qianfan.spi;



import dev.langchain4j.model.qianfan.QianfanLanguageModel;

import java.util.function.Supplier;

/**
 * A factory for building {@link QianfanLanguageModel.QianfanLanguageModelBuilder} instances.
 */
public interface QianfanLanguageModelBuilderFactory extends Supplier<QianfanLanguageModel.QianfanLanguageModelBuilder> {
}
