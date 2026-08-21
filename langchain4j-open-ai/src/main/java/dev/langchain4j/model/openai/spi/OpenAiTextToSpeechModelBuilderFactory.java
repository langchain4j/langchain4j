package dev.langchain4j.model.openai.spi;

import dev.langchain4j.Internal;
import dev.langchain4j.model.openai.OpenAiTextToSpeechModel;
import java.util.function.Supplier;

/**
 * A factory for building {@link OpenAiTextToSpeechModel.Builder} instances.
 */
@Internal
public interface OpenAiTextToSpeechModelBuilderFactory extends Supplier<OpenAiTextToSpeechModel.Builder> {}
