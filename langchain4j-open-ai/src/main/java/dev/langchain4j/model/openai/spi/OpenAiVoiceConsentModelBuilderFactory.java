package dev.langchain4j.model.openai.spi;

import dev.langchain4j.Internal;
import dev.langchain4j.model.openai.OpenAiVoiceConsentModel;
import java.util.function.Supplier;

/**
 * A factory for building {@link OpenAiVoiceConsentModel.Builder} instances.
 */
@Internal
public interface OpenAiVoiceConsentModelBuilderFactory extends Supplier<OpenAiVoiceConsentModel.Builder> {}
