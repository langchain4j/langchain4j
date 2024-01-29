package dev.langchain4j.model.qianfan.spi;



import dev.langchain4j.model.qianfan.QianfanEmbeddingModel;

import java.util.function.Supplier;

/**
 * A factory for building {@link QianfanEmbeddingModel.QianfanEmbeddingModelBuilder} instances.
 */
public interface QianfanEmbeddingModelBuilderFactory extends Supplier<QianfanEmbeddingModel.QianfanEmbeddingModelBuilder> {
}
