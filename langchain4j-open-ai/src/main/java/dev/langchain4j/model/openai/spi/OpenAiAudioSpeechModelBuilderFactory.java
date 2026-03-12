package dev.langchain4j.model.openai.spi;

import dev.langchain4j.Internal;
import dev.langchain4j.model.openai.OpenAiAudioSpeechModel;
import java.util.function.Supplier;

/**
 * A factory for building {@link OpenAiAudioSpeechModel.Builder} instances.
 */
@Internal
public interface OpenAiAudioSpeechModelBuilderFactory extends Supplier<OpenAiAudioSpeechModel.Builder> {}
