package dev.langchain4j.model.workersai.spi;

import dev.langchain4j.model.workersai.WorkersAiLanguageModel;

import java.util.function.Supplier;

/**
 * A factory for building {@link WorkersAiLanguageModel.Builder} instances.
 */
public interface WorkersAiLanguageModelBuilderFactory extends Supplier<WorkersAiLanguageModel.Builder> {
}
