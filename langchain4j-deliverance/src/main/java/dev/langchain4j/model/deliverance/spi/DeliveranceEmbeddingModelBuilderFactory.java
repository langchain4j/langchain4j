package dev.langchain4j.model.deliverance.spi;

import dev.langchain4j.model.deliverance.DeliveranceEmbeddingModel;

import java.util.function.Supplier;

public interface DeliveranceEmbeddingModelBuilderFactory extends Supplier<DeliveranceEmbeddingModel.DeliveranceEmbeddingModelBuilder> {
}
