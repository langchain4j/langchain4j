package dev.langchain4j.model.openai.spi;

import dev.langchain4j.Internal;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;

import java.util.function.Supplier;

/**
 * A factory for building {@link dev.langchain4j.model.openai.OpenAiEmbeddingModel.OpenAiEmbeddingModelBuilder} instances.
 */
@Internal
public interface OpenAiEmbeddingModelBuilderFactory extends Supplier<OpenAiEmbeddingModel.OpenAiEmbeddingModelBuilder> {
}
