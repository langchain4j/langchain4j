package dev.langchain4j.model.openai.spi;

import dev.langchain4j.Internal;
import dev.langchain4j.model.openai.OpenAiDecisionModel;
import java.util.function.Supplier;

/**
 * A factory for building {@link OpenAiDecisionModel.OpenAiDecisionModelBuilder} instances.
 */
@Internal
public interface OpenAiDecisionModelBuilderFactory extends Supplier<OpenAiDecisionModel.OpenAiDecisionModelBuilder> {}
