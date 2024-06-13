package dev.langchain4j.model.workersai.spi;

import dev.langchain4j.model.workersai.WorkersAiChatModel;

import java.util.function.Supplier;

/**
 * A factory for building {@link WorkersAiChatModel.Builder} instances.
 */
public interface WorkersAiChatModelBuilderFactory extends Supplier<WorkersAiChatModel.Builder> {
}
