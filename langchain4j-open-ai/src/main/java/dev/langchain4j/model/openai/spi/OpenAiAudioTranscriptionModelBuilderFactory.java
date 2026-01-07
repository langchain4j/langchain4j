package dev.langchain4j.model.openai.spi;

import dev.langchain4j.Internal;
import dev.langchain4j.model.openai.OpenAiAudioTranscriptionModel;

import java.util.function.Supplier;

/**
 * A factory for building {@link OpenAiAudioTranscriptionModel.Builder} instances.
 */
@Internal
public interface OpenAiAudioTranscriptionModelBuilderFactory extends Supplier<OpenAiAudioTranscriptionModel.Builder> {}
