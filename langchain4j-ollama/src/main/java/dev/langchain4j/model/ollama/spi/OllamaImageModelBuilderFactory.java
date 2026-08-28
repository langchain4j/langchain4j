package dev.langchain4j.model.ollama.spi;

import dev.langchain4j.model.ollama.OllamaImageModel;
import java.util.function.Supplier;

/**
 * A factory for building {@link OllamaImageModel.OllamaImageModelBuilder} instances.
 */
public interface OllamaImageModelBuilderFactory extends Supplier<OllamaImageModel.OllamaImageModelBuilder> {}
