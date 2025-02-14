package dev.langchain4j.model.openaiofficial.spi;

import dev.langchain4j.model.openaiofficial.OpenAiOfficialEmbeddingModel;
import java.util.function.Supplier;

/**
 * A factory for building {@link OpenAiOfficialEmbeddingModel} instances.
 */
public interface OpenAiOfficialEmbeddingModelBuilderFactory extends Supplier<OpenAiOfficialEmbeddingModel.Builder> {}
