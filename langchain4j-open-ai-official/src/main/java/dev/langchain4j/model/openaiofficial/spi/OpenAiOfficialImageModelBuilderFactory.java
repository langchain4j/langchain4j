package dev.langchain4j.model.openaiofficial.spi;

import dev.langchain4j.model.openaiofficial.OpenAiOfficialImageModel;
import java.util.function.Supplier;

/**
 * A factory for building {@link OpenAiOfficialImageModel} instances.
 */
public interface OpenAiOfficialImageModelBuilderFactory
        extends Supplier<OpenAiOfficialImageModel.Builder> {}
