package dev.langchain4j.model.deliverance.spi;

import dev.langchain4j.model.deliverance.DeliveranceStreamingLanguageModel;

import java.util.function.Supplier;

public interface DeliveranceStreamingLanguageModelBuilderFactory extends Supplier<DeliveranceStreamingLanguageModel.DeliveranceStreamingLanguageModelBuilder> {
}
