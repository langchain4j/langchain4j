package dev.langchain4j.model.mistralai.spi;

import dev.langchain4j.model.mistralai.MistralAiStreamingFimModel;
import java.util.function.Supplier;

/**
 * A factory for building {@link MistralAiStreamingFimModel.MistralAiStreamingFimModelBuilder} instances.
 */
public interface MistralAiStreamingFimModelBuilderFactory
        extends Supplier<MistralAiStreamingFimModel.MistralAiStreamingFimModelBuilder> {}
