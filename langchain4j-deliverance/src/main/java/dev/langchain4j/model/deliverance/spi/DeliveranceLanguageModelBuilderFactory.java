package dev.langchain4j.model.deliverance.spi;

import dev.langchain4j.model.deliverance.DeliveranceLanguageModel;

import java.util.function.Supplier;

public interface DeliveranceLanguageModelBuilderFactory extends Supplier<DeliveranceLanguageModel.DeliveranceLanguageModelBuilder> {
}
