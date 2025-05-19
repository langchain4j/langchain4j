package dev.langchain4j.model.mistralai.spi;

import dev.langchain4j.model.mistralai.MistralAiFimModel;
import java.util.function.Supplier;

/**
 * A factory for building {@link MistralAiFimModel.MistralAiFimModelBuilder} instances.
 */
public interface MistralAiFimModelBuilderFactory extends Supplier<MistralAiFimModel.MistralAiFimModelBuilder> {}
