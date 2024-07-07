package dev.langchain4j.model.workersai.spi;

import dev.langchain4j.model.workersai.WorkersAiImageModel;
import dev.langchain4j.model.workersai.WorkersAiLanguageModel;

import java.util.function.Supplier;

/**
 * A factory for building {@link WorkersAiImageModel.Builder} instances.
 */
public interface WorkersAiImageModelBuilderFactory extends Supplier<WorkersAiImageModel.Builder> {
}
