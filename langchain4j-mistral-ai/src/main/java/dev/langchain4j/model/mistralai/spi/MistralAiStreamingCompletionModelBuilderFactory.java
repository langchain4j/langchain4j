package dev.langchain4j.model.mistralai.spi;

import dev.langchain4j.model.mistralai.MistralAiStreamingCompletionModel;

import java.util.function.Supplier;

/**
 * A factory for building {@link MistralAiStreamingCompletionModel.MistralAiStreamingCompletionModelBuilder} instances.
 */
public interface MistralAiStreamingCompletionModelBuilderFactory extends Supplier<MistralAiStreamingCompletionModel.MistralAiStreamingCompletionModelBuilder> {
}
