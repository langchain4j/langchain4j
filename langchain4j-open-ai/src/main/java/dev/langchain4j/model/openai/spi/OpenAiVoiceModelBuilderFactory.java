package dev.langchain4j.model.openai.spi;

import dev.langchain4j.Internal;
import dev.langchain4j.model.openai.OpenAiVoiceModel;
import java.util.function.Supplier;

/**
 * A factory for building {@link OpenAiVoiceModel.Builder} instances.
 */
@Internal
public interface OpenAiVoiceModelBuilderFactory extends Supplier<OpenAiVoiceModel.Builder> {}
